package com.example.bettereve.mixin;

import com.example.bettereve.entitylv.EntityLevelSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.WitherSkull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让凋灵头颅的伤害吃剧本等级表加成 —— 这是给凋灵开的专门口子，不是通用机制。
 *
 * <p><b>为什么必须单独接</b>：凋灵本体没有任何近战 Goal（{@code registerGoals} 里只有
 * {@code RangedAttackGoal}），它的伤害 100% 来自凋灵头颅。而头颅的命中伤害是写死的字面量：
 *
 * <pre>
 * // net.minecraft.world.entity.projectile.WitherSkull#onHitEntity
 * flag = entity.hurt(this.damageSources().witherSkull(this, livingentity), 8.0F);
 * </pre>
 *
 * <p>投掷物那条等级通路也够不着它：{@link EntityLevelSystem#setEntityLevel} 对投射物走
 * {@code applyProjectileLevel}（只认 {@code AbstractArrow} 的 {@code getBaseDamage/setBaseDamage}），
 * 而 {@code WitherSkull} 继承的是 {@code AbstractHurtingProjectile → Projectile}，
 * 落到 {@code applyGenericProjectileLevel} —— 那个方法是空实现，因为纯 {@code Projectile}
 * 没有可承载伤害的字段。所以头颅身上**有等级、没有地方放**，只能在伤害产生点乘倍率。
 *
 * <p><b>等级取谁的</b>：头颅自己的等级（由 {@code options.projectile.inheritLevel} 从凋灵继承，
 * 见 {@code ProjectileFactionInheritanceHandler}），与 {@code applyProjectileLevel} 对箭的
 * 口径一致 —— 不回头查发射者。头颅没等级时倍率为 {@code 1.0}，这一击就是原版的 8.0F。
 *
 * <p>本 mixin <b>只管命中生物那一击</b>；头颅撞方块触发的爆炸（威力 1.0F）与凋灵自身
 * 无敌期结束的爆炸（威力 7.0F，只有搭 T 形祭坛生成时才走得到）都不在范围内。
 */
@Mixin(WitherSkull.class)
public abstract class WitherSkullMixin {

    /**
     * 重定向 {@code onHitEntity} 里那句 {@code Entity#hurt}，把金额乘上头颅自己的等级表攻击倍率。
     *
     * <p>{@code onHitEntity} 里有<b>两处</b> {@code Entity#hurt}：发射者是生物时 8.0F、
     * 发射者不是生物（含凋灵已死、归属查不到）时 5.0F。这里不限定 ordinal ——
     * handler 对金额做的是同一个乘法，命中一处还是两处都只会把该乘的乘上，不会写错；
     * 且头颅的等级在两种分支下都是它自己身上的那份。
     *
     * <p>handler 是实例方法（{@code onHitEntity} 是实例方法），{@code this} 即那只头颅，
     * 实例按 {@code (WitherSkull)(Object)this} 取（同 {@code WitherBossMixin} /
     * {@code MobAttackDistanceMixin} 的写法）。
     */
    @Redirect(
        method = "onHitEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        )
    )
    private boolean bettereve$scaleWitherSkullDamage(Entity target, DamageSource source, float amount) {
        WitherSkull self = (WitherSkull)(Object)this;
        double multiplier = EntityLevelSystem.getAttackMultiplier(self);
        return target.hurt(source, (float)(amount * multiplier));
    }
}
