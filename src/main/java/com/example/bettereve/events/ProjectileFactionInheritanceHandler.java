package com.example.bettereve.events;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.ProjectileRule;
import com.example.bettereve.faction.SummonerResolver;
import com.example.bettereve.entitylv.EntityLevelSystem;
import com.example.bettereve.network.FactionSyncHandler;
import com.example.bettereve.Bettereve;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 投掷物阵营/等级继承 —— 与 {@link SummonedFactionInheritanceHandler} 共用同一套归属证据链，
 * 但只负责"投掷物语义"实体，且规则更严（存档加载的实体一律跳过）。
 *
 * 职责边界（与召唤物 handler 严格互斥，同一实体绝不会被两边各加一次等级）：
 *   1. Projectile 家族（原版箭/雪球/火球 + 模组 AbstractArrow 之类）→ 本 handler
 *   2. 其余 LivingEntity（含 projectile_slash 这类"名字像投掷物、实际是 Mob"的实体）
 *      → 召唤物 handler。它同样按 NBT 痕迹解析归属（实测咒术回战 projectile_slash 的
 *        OWNER_UUID 就是它拿到的），并且额外负责血条、计分、目标继承等生物侧的事。
 *   3. 既不是 Projectile 也不是生物、但名字像投掷物的纯 Entity → 本 handler 兜底。
 *
 * 为什么分两遍判定：
 * 实测 JujutsuCraft 的生成点两种顺序都有 —— 一半在 addFreshEntity 之前就写好归属
 * （AIMalevolentShrineProcedure / AuthenticMutualLoveProcedure 等：先写 OWNER_UUID
 * 再 addFreshEntity），另一半在 EntityType.spawn() 返回之后才写
 * （DismantleProcedure → SetRangedAmmoProcedure：spawn 内部就触发了本事件，那时 NBT 还是空的）。
 * 所以：加入世界时判一次，判不出来就挂起，服务器 tick 末再判。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProjectileFactionInheritanceHandler {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/ProjectileFaction");

    /**
     * 延迟判定的挂起队列（服务器主线程独占，无需加锁）。
     *
     * 重判轮数上限 3：实测咒术回战在 spawn 同 tick 内就写完痕迹（同 tick 末一次就够），
     * 多留两轮是给"首个飞行 tick 才写痕迹"的模组留缓冲（实体在生成当 tick 不参与 tick，
     * 第一个飞行 tick 是下一 tick）。上限必须小：有些模组的弹药在命中时会把 OWNER_UUID
     * 改写成被打者（ChangeDamage1Procedure 的喜剧人反制），窗口越长越可能把"反制后的
     * 归属"当成出生归属。
     */
    private static final int MAX_RETRY_ROUNDS = 3;

    private static final List<Pending> PENDING = new ArrayList<>();

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只在服务器端处理；存档/区块加载的实体一律跳过：
        // 阵营与等级都已随实体存盘，重判一次会把箭伤倍率与穿透等级重复叠乘
        // （AbstractArrow 的 damage / PierceLevel 都写在实体 NBT 里，见 addAdditionalSaveData）。
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!handles(entity)) {
            return;
        }

        // 投掷物机制总开关：关掉后阵营与等级都不继承
        if (!ProjectileRule.enabled()) {
            return;
        }

        // 已有阵营 → 绝不覆盖：保护剧本 factionOverrides（ScenarioFactionOverrideHandler
        // 以 HIGHEST 优先级先写）与指令显式指定，避免投掷物继承把它吃掉。
        if (!FactionHelper.getEntityFactionId(entity).isEmpty()) {
            return;
        }

        if (!applyInheritance(entity)) {
            // 本 tick 拿不到证据，挂起到 tick 末再试（部分模组 spawn 之后才写 NBT）
            PENDING.add(new Pending(entity, MAX_RETRY_ROUNDS));
        }
    }

    /**
     * 服务器 tick 结束：对挂起的投掷物做延迟判定，捞取"spawn 之后才写入"的归属痕迹。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }

        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();

        for (Pending pending : batch) {
            Entity entity = pending.entity;
            // 实体可能在同 tick 内被移除 / 换维度
            if (entity.isRemoved() || !(entity.level() instanceof ServerLevel)) {
                continue;
            }
            if (!ProjectileRule.enabled()) {
                continue;
            }
            // 期间已被其它途径赋予阵营（剧本覆盖、指令、别的继承路径）→ 不再动它
            if (!FactionHelper.getEntityFactionId(entity).isEmpty()) {
                continue;
            }
            if (applyInheritance(entity)) {
                continue;
            }
            // 仍无证据：继续挂起，直到轮数用完为止（用完即放弃，投掷物保持无阵营）
            if (--pending.roundsLeft > 0) {
                PENDING.add(pending);
            }
        }
    }

    /**
     * 本 handler 负责的实体。
     *
     * Projectile 家族一律收；"名字像投掷物"的实体只在它不是生物时收 ——
     * 生物形态的（projectile_slash / entity_slash / bullet_skull 等）交给召唤物 handler，
     * 那边负责的正是生物侧的一切（血条、计分、目标），两边都判会导致等级被叠乘两次。
     */
    private static boolean handles(Entity entity) {
        if (entity instanceof Projectile) {
            return true;
        }
        return !(entity instanceof LivingEntity) && ProjectileRule.isProjectileLike(entity);
    }

    /**
     * 执行一次完整判定。
     *
     * @return true = 证据链已给出归属（无论主体有没有阵营）；false = 本轮无证据（需挂起重判）
     */
    private static boolean applyInheritance(Entity entity) {
        // 归属解析：统一走 SummonerResolver（TraceableEntity / OwnableEntity / NBT 痕迹）。
        // NBT 痕迹是模组投掷物的主通道 —— 实测咒术回战用 SetRangedAmmoProcedure 把发射者
        // UUID 以字符串写进自己的 OWNER_UUID 键，由 SummonedMarker 的关键词表读到。
        Entity owner = SummonerResolver.resolve(entity);
        if (owner == null) {
            return false;
        }

        boolean wrote = false;

        // 发射者有阵营时，投掷物继承阵营；无阵营(空串)则不设置
        if (ProjectileRule.inheritFaction()) {
            String factionId = FactionHelper.getEntityFactionId(owner);
            if (!factionId.isEmpty()) {
                FactionHelper.setEntityFactionId(entity, factionId);
                wrote = true;
            }
        }

        // 继承发射者的等级。
        // 只对"自身还没有等级"的投掷物施加：加成乘的是当前基础伤害/穿透
        // （EntityLevelSystem.applyProjectileLevel），重复施加会叠乘 ——
        // 实体二次加入世界（换维度等）时不至于再乘一遍。
        if (ProjectileRule.inheritLevel() && EntityLevelSystem.getEntityLevel(entity) <= 0) {
            int level = EntityLevelSystem.getEntityLevel(owner);
            if (level > 0) {
                EntityLevelSystem.setEntityLevel(entity, level);
                wrote = true;
                LOGGER.debug("投掷物继承等级: 实体类型={}, 发射者UUID={}, 发射者等级={}, 投掷物UUID={}",
                    entity.getType().toString(), owner.getUUID(), level, entity.getUUID());
            }
        }

        // 写入后立即同步到客户端：EntityJoinLevelEvent 那次同步发生在写入之前，
        // 不补发的话客户端侧的 canHitEntity 读不到阵营（只影响观感，伤害以服务端为准）。
        if (wrote) {
            FactionSyncHandler.syncEntityImmediately(entity);
        }
        return true;
    }

    /** 挂起条目：实体 + 剩余重判轮数 */
    private static final class Pending {
        final Entity entity;
        int roundsLeft;

        Pending(Entity entity, int roundsLeft) {
            this.entity = entity;
            this.roundsLeft = roundsLeft;
        }
    }
}
