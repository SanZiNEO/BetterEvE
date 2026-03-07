package com.example.bettereve.events;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.level.LevelSystem;
import com.example.bettereve.Bettereve;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
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

        // 严格检查是否是有效的投掷物类型，确保只处理真正的投掷物
        if (entity instanceof Projectile && isStrictlyValidProjectileType(entity)) {
            Projectile projectile = (Projectile) entity;
            
            // 获取发射者
            Entity owner = projectile.getOwner();
            if (owner == null) {
                return;
            }

            // 获取发射者的阵营ID
            String factionId = FactionHelper.getEntityFactionId(owner);
            
            // 如果发射者不是中立阵营，则设置投掷物的阵营与发射者相同
            if (!factionId.equals("neutral")) {
                FactionHelper.setEntityFactionId(projectile, factionId);
                LOGGER.debug("投掷物继承阵营: 投掷物类型={}, 发射者UUID={}, 发射者阵营={}, 投掷物UUID={}", 
                    entity.getType().toString(), owner.getUUID(), factionId, entity.getUUID());
            }

            // 继承发射者的等级
            int level = LevelSystem.getEntityLevel(owner);
            if (level > 0) {
                LevelSystem.setEntityLevel(projectile, level);
                LOGGER.debug("投掷物继承等级: 实体类型={}, 发射者UUID={}, 发射者等级={}, 投掷物UUID={}", 
                    entity.getType().toString(), owner.getUUID(), level, entity.getUUID());
            }
        }
    }

    // 严格判断是否是有效的投掷物类型，避免对其他实体的误操作
    private static boolean isStrictlyValidProjectileType(Entity entity) {
        // 只接受 Minecraft 原版定义的投射物类型
        String entityTypeName = entity.getType().toString().toLowerCase();
        
        // 严格限制的有效投掷物类型：
        return entityTypeName.contains("arrow") ||
               entityTypeName.contains("fireball") ||
               entityTypeName.contains("snowball") ||
               entityTypeName.contains("egg") ||
               entityTypeName.contains("ender_pearl") ||
               entityTypeName.contains("trident") ||
               entityTypeName.contains("experience_bottle") ||
               entityTypeName.contains("potion") ||
               entityTypeName.contains("shulker_bullet") ||
               entityTypeName.contains("llama_spit") ||
               entityTypeName.contains("spectral_arrow") ||
               entityTypeName.contains("tipped_arrow") ||
               entityTypeName.contains("dragon_fireball") ||
               entityTypeName.contains("small_fireball") ||
               entityTypeName.contains("wither_skull");
    }
}
