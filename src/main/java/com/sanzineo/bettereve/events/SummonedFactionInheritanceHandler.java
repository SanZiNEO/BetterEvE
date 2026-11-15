package com.sanzineo.bettereve.events;

import com.sanzineo.bettereve.entitylv.EntityLevelSystem;
import com.sanzineo.bettereve.faction.FactionHelper;
import com.sanzineo.bettereve.faction.SummonRule;
import com.sanzineo.bettereve.faction.SummonedMarker;
import com.sanzineo.bettereve.network.FactionSyncHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 召唤物阵营继承 —— 痕迹驱动，零反射。
 *
 * 设计铁律：证据不存在时什么都不做。宁可漏，绝不误判。
 * 不做任何"附近实体扫描"：早期版本用 15 格半径猜召唤者，混战中会把召唤物
 * 归给离得最近的无关生物，已废弃；并且原版僵尸求援的生成距离是 7~40 格，
 * 半径法在原理上就覆盖不到。
 *
 * 为什么分两遍判定：
 * 部分模组（实测 JujutsuCraft）在 EntityType.spawn() 之后才写召唤者 NBT，
 * 而 EntityJoinLevelEvent 在 spawn 内部触发 —— 事件时刻 NBT 还是空的。
 * 因此：加入世界时先试一遍，未能判定则挂起，服务器 tick 结束时再试一遍。
 *
 * 判定链（严格顺序，命中即继承）：
 *   0. 客户端 / 非 Mob / 存档加载 → 返回
 *   1. 已有非空阵营               → 返回（保护 /bettereve spawn、投掷物继承等显式指定路径）
 *   2. TraceableEntity.getOwner()          原版尖牙/恼鬼/投掷物
 *   3. OwnableEntity.getOwner()            标准可驯服/从者体系
 *   4. OwnableEntity.getOwnerUUID() 反查     owner 未加载到本维度时
 *   5. NBT 召唤者痕迹                       ★ 跨模组主通道（实测 JujutsuCraft 等）
 *
 * 已删除的旧规则："getTarget() 就是召唤者"的目标反推。反编译实测（Alex's Mobs 的
 * 愚人节海熊、咒术回战的攻击实体）表明出生带的目标是**被打者**而不是召唤者，
 * 按它继承会把阵营写反。详见 resolveSummoner() 末尾的说明。
 */
@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SummonedFactionInheritanceHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/SummonedInheritance");

    /** 多部件生物父链上溯的最大深度（防环状链接死循环） */
    private static final int MAX_PARENT_CHAIN_DEPTH = 32;

    /** 延迟判定的挂起队列（服务器主线程独占，无需加锁） */
    private static final List<LivingEntity> PENDING = new ArrayList<>();

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();

        // 本项目职责边界（重要）：
        //   - 投掷物（Projectile 及其子类，如箭 / 咒术回战的 bullet_knife）由
        //     ProjectileFactionInheritanceHandler 负责阵营与等级继承，**且投掷物不显示血条**；
        //   - 本 handler 只处理"生物"：Mob 与多部件生物的部件（LivingEntity 但不是 Mob，
        //     如 Alex's Mobs 虚空蠕虫的身体节，带 ParentUUID 指向上游）。
        // 所以必须显式排除 Projectile，避免把投掷物误当召唤物而去改动它的血条行为。
        if (entity instanceof Projectile) {
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        boolean isMultipartPiece = SummonedMarker.isMultipartPiece(living);
        if (!(living instanceof Mob) && !isMultipartPiece) {
            return;
        }

        // 规则 1：已有阵营 → 绝不覆盖
        if (!FactionHelper.getEntityFactionId(living).isEmpty()) {
            return;
        }
        if (!SummonRule.enabled()) {
            return;
        }

        // 已有召唤物/部件标记说明处理过，不重复
        if (SummonedMarker.isSummoned(living)) {
            return;
        }

        try {
            if (!applyInheritance(living)) {
                // 本 tick 拿不到证据，挂起到 tick 结束再试（部分模组后写 NBT）
                PENDING.add(living);
            }
        } catch (Throwable t) {
            logAndRethrow(living, "加入世界判定", t);
        }
    }

    /**
     * 服务器 tick 结束：对挂起实体做第二次判定，捞取"spawn 之后才写入 NBT"的召唤痕迹。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }

        List<LivingEntity> batch = new ArrayList<>(PENDING);
        PENDING.clear();

        for (LivingEntity living : batch) {
            // 实体可能在同 tick 内已被移除
            if (living.isRemoved() || !(living.level() instanceof ServerLevel)) {
                continue;
            }
            // 期间已被其它途径赋予阵营或已标记，跳过
            if (!FactionHelper.getEntityFactionId(living).isEmpty() || SummonedMarker.isSummoned(living)) {
                continue;
            }
            if (!SummonRule.enabled()) {
                continue;
            }
            try {
                applyInheritance(living);
            } catch (Throwable t) {
                logAndRethrow(living, "tick 延迟判定", t);
            }
        }
    }

    private static void logAndRethrow(LivingEntity living, String stage, Throwable t) {
        // 记录上下文后原样重抛：异常进入 Forge 崩溃报告，日志补足"是哪只实体触发的"
        LOGGER.error("[BetterEvE] 召唤物阵营继承失败（{}）entity={} type={} 位置=({}, {}, {})",
                stage, living.getUUID(), living.getType(),
                String.format("%.1f", living.getX()),
                String.format("%.1f", living.getY()),
                String.format("%.1f", living.getZ()), t);
        if (t instanceof RuntimeException re) {
            throw re;
        } else if (t instanceof Error err) {
            throw err;
        }
        throw new RuntimeException(t);
    }

    /**
     * 执行一次完整判定。
     *
     * @return true = 已判定并完成继承；false = 本轮无证据（需挂起重试）
     */
    private static boolean applyInheritance(LivingEntity living) {
        SummonerRef ref = resolveSummoner(living);
        if (ref == null) {
            return false;
        }

        // 先标记：无论能否继承到阵营，归属关系本身是事实
        SummonedMarker.markSummoned(living, ref.reason,
                ref.summoner != null ? ref.summoner.getUUID() : ref.summonerUuid);

        Entity summoner = ref.summoner;
        boolean isMultipart = SummonedMarker.REASON_PARENT.equals(ref.reason);

        // ② 继承阵营：仅当主体有阵营（避免写入空阵营把召唤物变成中立摆设）
        if (SummonRule.inheritFaction() && summoner != null) {
            String summonerFaction = FactionHelper.getEntityFactionId(summoner);
            if (!summonerFaction.isEmpty()) {
                FactionHelper.setEntityFactionId(living, summonerFaction);
                LOGGER.debug("[召唤继承] {} 继承阵营 {} ← 主体 {} ({})",
                        living.getType(), summonerFaction, summoner.getType(), ref.reason);
            }
        }

        // ②b 继承等级 —— 必须在设完阵营之后：
        // EntityLevelSystem.setEntityLevel 会按"实体自己的阵营"去剧本查等级配置，
        // 阵营还没写进去的话查不到配置，加成会整个失效。
        if (SummonRule.inheritLevel() && summoner != null) {
            int summonerLevel = EntityLevelSystem.getEntityLevel(summoner);
            if (summonerLevel > 0) {
                EntityLevelSystem.setEntityLevel(living, summonerLevel);
                LOGGER.debug("[召唤继承] {} 继承等级 {} ← 主体 {} ({})",
                        living.getType(), summonerLevel, summoner.getType(), ref.reason);
            }
        }

        // ③④ 仅对 Mob 生效
        if (living instanceof Mob mob && !isMultipart) {
            // 继承战意（部件没有 AI，不需要）
            if (summoner instanceof Mob summonerMob && summonerMob.getTarget() != null) {
                mob.setTarget(summonerMob.getTarget());
            }
            // 血条规则：是生物就有血条，是投掷物才没有（投掷物不走这个 handler）。
            // 少数外观类召唤物（如多部件生物的每一节）挂血条很难看，
            // 可用剧本 options.summon.healthBarOverrides 按实体类型精确关掉。
            Boolean override = SummonRule.healthBarOverrideFor(mob);
            if (override != null) {
                FactionHelper.setEntityShowHealthBar(mob, override);
            } else {
                FactionHelper.setEntityShowHealthBar(mob, true);
            }
        }

        // ⑤ 同步到客户端
        if (living instanceof Mob mobForSync) {
            FactionSyncHandler.syncEntityImmediately(mobForSync);
        }

        return true;
    }

    /** 按判定链解析主体。全不命中返回 null。 */
    private static SummonerRef resolveSummoner(LivingEntity living) {
        // 规则 1.5：多部件生物的部件 —— 沿 ParentUUID 逐节上溯到主体
        // （Alex's Mobs 虚空蠕虫/森蚺/蜈蚣的身体节走这条）
        if (SummonedMarker.isMultipartPiece(living)) {
            LivingEntity root = resolveMultipartRoot(living);
            if (root != null && root != living) {
                // 顺带把整体登记出来：主体标记落一次即可，用于后续"整体结算"判定
                if (!SummonedMarker.isMultipartRoot(root)) {
                    SummonedMarker.markMultipartRoot(root);
                    LOGGER.debug("[多部件] 登记整体 {} (根)，当前部件 {} ({})",
                            root.getType(), living.getType(), living.getUUID());
                }
                return new SummonerRef(root, root.getUUID(), SummonedMarker.REASON_PARENT);
            }
        }

        Mob mob = living instanceof Mob m ? m : null;
        if (mob == null) {
            // 非 Mob 的部件如果 ParentUUID 解析不到实体，本轮无证据
            return null;
        }

        // 规则 2：TraceableEntity —— 原版尖牙/恼鬼/投掷物
        if (mob instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            if (owner != null) {
                return new SummonerRef(owner, owner.getUUID(), SummonedMarker.REASON_OWNER);
            }
        }

        // 规则 3/4：OwnableEntity
        if (mob instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner != null) {
                return new SummonerRef(owner, owner.getUUID(), SummonedMarker.REASON_OWNER_UUID);
            }
            UUID ownerUuid = ownable.getOwnerUUID();
            if (ownerUuid != null) {
                SummonerRef viaUuid = byUuid(mob, ownerUuid, SummonedMarker.REASON_OWNER_UUID);
                if (viaUuid != null) {
                    return viaUuid;
                }
            }
        }

        // 规则 5：NBT 召唤者痕迹（跨模组主通道）
        UUID nbtUuid = SummonedMarker.resolveSummonerUuid(mob);
        if (nbtUuid != null) {
            SummonerRef viaNbt = byUuid(mob, nbtUuid, SummonedMarker.REASON_OWNER_NBT);
            if (viaNbt != null) {
                return viaNbt;
            }
        }

        // 到此为止，没有任何证据 → 本轮判不出来（挂起到 tick 末再试）。
        //
        // 这里曾经有一条"规则 6：getTarget() 就是召唤者"的反推，已删除。
        // 反编译证据（Alex's Mobs 1.22.9 / JujutsuCraft ver50.1）：
        //   - "出生当刻已被设了目标"的唯一实例是 Alex's Mobs 的愚人节海熊
        //     （ServerEvents.java:825-826：bear.setTarget(玩家) → addFreshEntity(bear)），
        //     目标是**被打者**，不是召唤者；
        //   - 咒术回战生成攻击实体时根本不设目标（在 procedures/ 里检索 setTarget 调用，
        //     命中的全是 AI/选敌过程、且都在实体自己的 tick 里跑；projectile_slash 的
        //     生成点一个都不设目标），它的归属写在 OWNER_UUID 上，由规则 5 覆盖。
        // 也就是说，按"目标"继承拿到的是**被打者的阵营**：实体打不到自己的目标、
        // 反而能打召唤者一侧的人。本 handler 的铁律是"证据不存在时什么都不做"，
        // 目标反推不满足这条，故删。判不出归属的攻击实体保持无阵营（混乱生物），
        // 需要时由剧本 options.summon.factionOverrides 直接钉阵营。
        return null;
    }

    /**
     * 沿 ParentUUID 链逐节上溯到多部件生物的主体（尾 → 身 → 头）。
     * 带深度上限防止环状链接导致死循环。
     */
    private static LivingEntity resolveMultipartRoot(LivingEntity piece) {
        LivingEntity current = piece;
        for (int depth = 0; depth < MAX_PARENT_CHAIN_DEPTH; depth++) {
            UUID parentUuid = SummonedMarker.getParentLinkUuid(current);
            if (parentUuid == null || !(current.level() instanceof ServerLevel serverLevel)) {
                return current;
            }
            Entity parent = serverLevel.getEntity(parentUuid);
            if (!(parent instanceof LivingEntity parentLiving) || parentLiving == current) {
                return current;
            }
            current = parentLiving;
        }
        return current;
    }

    /** 用 UUID 在当前维度里查实体；查不到返回 null */
    private static SummonerRef byUuid(LivingEntity living, UUID uuid, String reason) {
        if (!(living.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity found = serverLevel.getEntity(uuid);
        return found != null ? new SummonerRef(found, uuid, reason) : null;
    }

    /** 主体引用：实体 + UUID + 判定来源 */
    private static final class SummonerRef {
        final Entity summoner;
        final UUID summonerUuid;
        final String reason;

        SummonerRef(Entity summoner, UUID summonerUuid, String reason) {
            this.summoner = summoner;
            this.summonerUuid = summonerUuid;
            this.reason = reason;
        }
    }
}
