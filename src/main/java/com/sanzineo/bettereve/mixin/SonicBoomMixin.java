package com.sanzineo.bettereve.mixin;

import com.sanzineo.bettereve.entitylv.EntityLevelSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让监守者的音爆伤害吃剧本等级表加成 —— 这是给监守者开的专门口子，不是通用机制。
 *
 * <p><b>为什么必须单独接</b>：原版音爆的伤害是写死在行为里的字面量：
 *
 * <pre>
 * // net.minecraft.world.entity.ai.behavior.warden.SonicBoom#tick（lambda$tick$2 里）
 * target.hurt(level.damageSources().sonicBoom(warden), 10.0F);
 * </pre>
 *
 * <p>它既不读攻击者的 {@code ATTACK_DAMAGE} 属性，也不走 {@code Mob.doHurtTarget}，
 * 所以 {@link EntityLevelSystem#applySpawnLevel} 那套「把倍率写进属性」的路子对它完全无效
 * —— 无论监守者多少级，音爆恒 10 点。等级表只能以倍率的形式在这里乘上去。
 *
 * <p><b>为什么是 lambda</b>：这句 {@code hurt} 在 {@code tick} 的
 * {@code Optional#ifPresent} 回调里，javac 会把它编成 {@code SonicBoom} 的私有静态合成方法
 * （官方名 {@code lambda$tick$2}，SRG 名 {@code m_217701_}，签名
 * {@code (Warden, ServerLevel, LivingEntity)V}），不在 {@code tick} 自己的字节码里，
 * 所以注入点必须写这个合成方法的名字而不是 {@code tick}。
 * 该映射条目在 {@code srg_to_official_1.20.1.tsrg} 里存在，refmap 能正常重映射。
 *
 * <p><b>为什么 {@code method} 里要连签名一起写</b>：只给名字 {@code "lambda$tick$2"} 时，
 * Mixin AP 会报 {@code Unable to determine descriptor for @Redirect target method}，
 * 于是**不往 refmap 写映射条目** —— 那样运行时拿名字去 SRG 类里找 {@code lambda$tick$2}
 * 会找不到，mixin 直接加载失败。连签名一起写（写法同 {@code EnderDragonMixin}）才能让 AP
 * 完成名字解析并产出 refmap 条目。
 *
 * <p><b>为什么用 {@code source.getEntity()} 而不是捕获外层局部变量</b>：
 * {@code @Redirect} 拿不到目标方法所在 lambda 的捕获参数（那只 {@code Warden}），
 * 但不需要 —— {@code damageSources().sonicBoom(warden)} 构造伤害源时就把监守者塞进了
 * {@code DamageSource}，{@code getEntity()} 读回来就是它。倍率因此不需要任何局部变量。
 *
 * <p><b>等级取谁的</b>：攻击者（监守者）自己的等级 + 它所属阵营的等级表 ——
 * 与近战完全同一个口径。没有等级 / 没有等级表时倍率为 {@code 1.0}，
 * 这一击就是原版的 10.0F，行为与没接一样。
 *
 * <p>伤害类型仍是原版的 {@code minecraft:sonic_boom}（无视护甲、受抗性提升减免），
 * 只是数值被乘了倍率 —— 不改变任何其它语义。
 */
@Mixin(SonicBoom.class)
public abstract class SonicBoomMixin {

    /**
     * 重定向 lambda 里那句 {@code LivingEntity#hurt}，把金额乘上监守者的等级表攻击倍率。
     *
     * <p>handler 的 static 修饰必须与被重定向的合成方法一致（{@code lambda$tick$2} 是静态的）。
     * 参数顺序 = 被重定向调用的「接收者 + 实参」：target 是 {@code LivingEntity}，
     * 后两个是 {@code hurt(DamageSource, float)} 的实参。
     */
    @Redirect(
        method = "lambda$tick$2(Lnet/minecraft/world/entity/monster/warden/Warden;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        )
    )
    private static boolean bettereve$scaleSonicBoomDamage(LivingEntity target, DamageSource source, float amount) {
        double multiplier = EntityLevelSystem.getAttackMultiplier(source.getEntity());
        return target.hurt(source, (float)(amount * multiplier));
    }
}
