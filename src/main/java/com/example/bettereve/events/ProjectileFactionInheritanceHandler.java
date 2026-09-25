package com.example.bettereve.events;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.ProjectileRule;
import com.example.bettereve.faction.SummonerResolver;
import com.example.bettereve.entitylv.EntityLevelSystem;
import com.example.bettereve.Bettereve;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProjectileFactionInheritanceHandler {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/ProjectileFaction");

    // 监听实体加入世界事件（用于实现投掷物阵营继承和等级继承）
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只在服务器端处理
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();

        // 归属解析：统一走 SummonerResolver（TraceableEntity / OwnableEntity / NBT 痕迹）
        // 为什么不继续用"类型名白名单"做唯一判据：
        // 白色名单只能覆盖原版那十来种投射物，模组自定义投射物（咒术回战的
        // bullet_knife 继承 AbstractArrow、还有各类 slash/needle/rock 之类）名字千奇百怪，
        // 靠枚举必然漏。
        // 而"能解析出 owner"本身就是很强的证据 —— 有主人的实体，继承主人的阵营是合理语义。
        Entity owner = SummonerResolver.resolveDirectOwner(entity);
        if (owner == null) {
            return;
        }

        // 保留类型闸门：只放行投射物，或"确实是投射物语义但非 Projectile 子类"的实体。
        // 目的是避免把 Mob 也当投射物处理（Mob 的归属由召唤物继承逻辑负责）。
        if (!(entity instanceof Projectile) && !ProjectileRule.isProjectileLike(entity)) {
            return;
        }

        // 投掷物机制总开关：关掉后阵营与等级都不继承
        if (!ProjectileRule.enabled()) {
            return;
        }

        // 发射者有阵营时，投掷物继承阵营；无阵营(空串)则不设置
        if (ProjectileRule.inheritFaction()) {
            String factionId = FactionHelper.getEntityFactionId(owner);
            if (factionId != null && !factionId.isEmpty()) {
                FactionHelper.setEntityFactionId(entity, factionId);
            }
        }

        // 继承发射者的等级
        if (ProjectileRule.inheritLevel()) {
            int level = EntityLevelSystem.getEntityLevel(owner);
            if (level > 0) {
                EntityLevelSystem.setEntityLevel(entity, level);
                LOGGER.debug("投掷物继承等级: 实体类型={}, 发射者UUID={}, 发射者等级={}, 投掷物UUID={}",
                    entity.getType().toString(), owner.getUUID(), level, entity.getUUID());
            }
        }
    }
}