package com.sanzineo.bettereve.mixin;

import com.sanzineo.bettereve.faction.FactionHelper;
import com.sanzineo.bettereve.faction.FactionRelationManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 投掷物的"同阵营穿过去"规则 —— 拦在命中判定层（canHitEntity）。
 *
 * 为什么拦在这里：Projectile 家族的命中检测全部经过 canHitEntity
 * （AbstractArrow.findHitEntity / AbstractHurtingProjectile.tick 都把它当谓词传给
 * ProjectileUtil.getEntityHitResult），返回 false 的实体**整根箭都当它不存在**：
 * 不受伤、不挡箭、不反弹、不消耗。这正是"友军不吃友军的箭"要的语义 ——
 * 若只在伤害层取消（CrystalSystem.onLivingAttack），箭仍会走
 * AbstractArrow.onHitEntity 的反弹分支，友军变成肉盾。
 *
 * 判据与伤害层同一口径：拦 ⇔ FactionHelper.isEntitiesHostile == false
 * （同阵营、或剧本未定义敌对关系）。投掷物一侧取"有效阵营"：
 * 自身还没继承到阵营时回退到发射者（见 FactionHelper.getEffectiveFactionId）。
 * 投掷物有效阵营为空时不拦 —— 无阵营的箭保持原版行为，绝不动 vanilla。
 *
 * 注意：本 mixin 客户端也会注入。客户端读不到服务端写进 NBT 的阵营时，
 * 这里不拦（观感差异，伤害仍以服务端为准）。
 */
@Mixin(Projectile.class)
public abstract class ProjectileCollisionMixin {

    // 重写 canHitEntity() 方法，使同阵营的实体无法被击中
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    protected void canHitEntity(Entity target, CallbackInfoReturnable<Boolean> cir) {
        // 获取当前投掷物实体
        Projectile projectile = (Projectile) (Object) this;

        // 投掷物的有效阵营：自身阵营；自身为空时回退到发射者。空 = 无从判定 → 不拦
        String projectileFactionId = FactionHelper.getEffectiveFactionId(projectile);
        if (projectileFactionId.isEmpty()) {
            return;
        }

        // 目标与投掷物不敌对（同阵营 / 剧本未定义敌对）→ 整根箭忽略该实体
        if (!FactionRelationManager.isHostile(projectileFactionId, FactionHelper.getEntityFactionId(target))) {
            cir.setReturnValue(false);
        }
    }
}
