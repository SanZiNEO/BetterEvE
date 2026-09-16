package com.example.bettereve.events;

import com.example.bettereve.faction.FactionHelper;
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
        if (!(entity instanceof Projectile) && !isProjectileLike(entity)) {
            return;
        }

        // 获取发射者的阵营ID
        String factionId = FactionHelper.getEntityFactionId(owner);

        // 发射者有阵营时，投掷物继承阵营；无阵营(空串)则不设置
        if (factionId != null && !factionId.isEmpty()) {
            FactionHelper.setEntityFactionId(entity, factionId);
        }

        // 继承发射者的等级
        int level = EntityLevelSystem.getEntityLevel(owner);
        if (level > 0) {
            EntityLevelSystem.setEntityLevel(entity, level);
            LOGGER.debug("投掷物继承等级: 实体类型={}, 发射者UUID={}, 发射者等级={}, 投掷物UUID={}",
                entity.getType().toString(), owner.getUUID(), level, entity.getUUID());
        }
    }

    /**
     * "投射物语义但非 Projectile 子类"的兜底识别。
     *
     * Projectile 子类（含 AbstractArrow）已经由 instanceof 覆盖，这里只处理
     * 模组自己实现、不继承 Projectile 的远程实体。
     *
     * 关键词来自整合包实测（咒术回战 bullet_knife / slash、各类 bullet 系），
     * 只作为后备判据：走到这里说明该实体已经能解析出 owner，
     * 归属关系是确定的，类型判断只为排除"被误当投射物的 Mob"。
     */
    private static boolean isProjectileLike(Entity entity) {
        String name = entity.getType().toString().toLowerCase();
        return name.contains("arrow") || name.contains("fireball") || name.contains("snowball")
                || name.contains("egg") || name.contains("pearl") || name.contains("trident")
                || name.contains("bottle") || name.contains("potion") || name.contains("bullet")
                || name.contains("_shot") || name.contains("shulker_bullet") || name.contains("llama_spit")
                || name.contains("knife") || name.contains("dagger") || name.contains("blade")
                || name.contains("spear") || name.contains("rock") || name.contains("shard")
                || name.contains("needle") || name.contains("spit") || name.contains("nail")
                || name.contains("kunai") || name.contains("shuriken") || name.contains("missile");
    }
}