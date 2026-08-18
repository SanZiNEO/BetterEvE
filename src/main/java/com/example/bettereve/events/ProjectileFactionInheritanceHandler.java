package com.example.bettereve.events;

import com.example.bettereve.faction.FactionHelper;
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

        // 检查是否是有效的投掷物类型（避免将非投掷物实体误设置阵营）
        if (entity instanceof Projectile && isValidProjectileType(entity)) {
            Projectile projectile = (Projectile) entity;
            
            // 获取发射者
            Entity owner = projectile.getOwner();
            if (owner == null) {
                return;
            }

            // 获取发射者的阵营ID
            String factionId = FactionHelper.getEntityFactionId(owner);
            
            // 发射者有阵营时，投掷物继承阵营；无阵营(空串)则不设置
            if (factionId != null && !factionId.isEmpty()) {
                FactionHelper.setEntityFactionId(projectile, factionId);
            }

            // 继承发射者的等级
            int level = EntityLevelSystem.getEntityLevel(owner);
            if (level > 0) {
                EntityLevelSystem.setEntityLevel(projectile, level);
                LOGGER.debug("投掷物继承等级: 实体类型={}, 发射者UUID={}, 发射者等级={}, 投掷物UUID={}", 
                    entity.getType().toString(), owner.getUUID(), level, entity.getUUID());
            }
        }
    }

    // 判断是否是有效的投掷物类型
    private static boolean isValidProjectileType(Entity entity) {
        String entityTypeName = entity.getType().toString().toLowerCase();
        
        // 有效的投掷物类型包括：
        // - arrow (箭)
        // - fireball (火球)
        // - snowball (雪球)
        // - egg (鸡蛋)
        // - ender_pearl (末影珍珠)
        // - trident (三叉戟)
        // - experience_bottle (经验瓶)
        // - potion (喷溅药水、滞留药水)
        // - shulker_bullet (潜影贝子弹)
        // - llama_spit ( llama 唾沫)
        
        return entityTypeName.contains("arrow") ||
               entityTypeName.contains("fireball") ||
               entityTypeName.contains("snowball") ||
               entityTypeName.contains("egg") ||
               entityTypeName.contains("ender_pearl") ||
               entityTypeName.contains("trident") ||
               entityTypeName.contains("experience_bottle") ||
               entityTypeName.contains("potion") ||
               entityTypeName.contains("shulker_bullet") ||
               entityTypeName.contains("llama_spit");
    }
}