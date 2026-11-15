package com.sanzineo.bettereve.mixin;

import com.sanzineo.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「阵营敌对」的攻击者能打凋灵 —— 只放行原版那句「同 MobType 免伤」。
 *
 * <p>原版 {@code WitherBoss.hurt} 在调 {@code super.hurt} 之前有一道门槛：
 *
 * <pre>
 * entity1 != null &amp;&amp; !(entity1 instanceof Player)
 *   &amp;&amp; entity1 instanceof LivingEntity
 *   &amp;&amp; ((LivingEntity)entity1).getMobType() == this.getMobType()   ← 只改这一处
 *   → return false
 * </pre>
 *
 * <p>凋灵自己的 MobType 是 {@code UNDEAD}，所以僵尸 / 骷髅 / 尸壳 / 溺尸 / 僵尸村民 /
 * 幻翼 / 僵尸马 / 骷髅马 / 僵尸疣猪兽 / 凋灵骷髅等一切亡灵打它都是 0 伤害
 * —— 打中了（{@code Mob.doHurtTarget} 返回 true）但血一点不掉，也不报错。
 *
 * <p>本 mixin 只把「攻击者那一侧」的 {@code getMobType()} 改成 {@code UNDEFINED}
 * （与原版的 {@code UNDEAD} 必然不等），于是原版其余门槛逐项保留：
 * 攻击者是 Player、攻击者是另一只凋灵、凋零系伤害（{@code WITHER_IMMUNE_TO}）、
 * 生成期无敌（{@code getInvulnerableTicks}）、半血后免疫箭（{@code isPowered} + 箭）
 * 全都照旧。**不**采用 {@code @Inject HEAD} 全接管（那是 EnderDragonMixin 的老路，
 * 会连原版 hurt 的其它分支一起重写）。
 *
 * <p>放行条件限定为「攻击者与凋灵阵营敌对」：双方都没有阵营时不敌对，所以原版世界
 * （无剧本 / 生存）行为逐项不变；只有剧本分了阵营、且阵营关系判为敌对时，
 * 亡灵才打得动凋灵。
 *
 * <p>写法说明：目标类 WitherBoss 是 Mob 的子类，但这里不需要访问父类成员，
 * 实例用 {@code (WitherBoss)(Object)this} 取（同 {@link MobAttackDistanceMixin}）。
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {

    /**
     * 重定向 {@code hurt} 里的 {@code getMobType()} 调用。
     *
     * <p>两个要点：
     * <ol>
     *   <li>Mixin 0.8.5 要求 handler 的 static 修饰与被重定向的方法一致；
     *       {@code getMobType()} 是实例方法，所以本方法也是实例方法，
     *       接收者作为第一个参数传入。</li>
     *   <li>{@code hurt} 里一共有两次 {@code getMobType()}（攻击者一次、{@code this} 一次），
     *       这里按接收者区分：{@code this} 那一侧原样返回，只有攻击者那一侧
     *       在阵营敌对时返回 {@code UNDEFINED} —— 无论注入器命中其中一次还是两次，
     *       那句比较都只会变成「不等」，不会变成「相等」。</li>
     * </ol>
     */
    @Redirect(
        method = "hurt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getMobType()Lnet/minecraft/world/entity/MobType;"
        )
    )
    private MobType bettereve$factionAwareMobType(LivingEntity receiver) {
        WitherBoss self = (WitherBoss)(Object)this;
        if (receiver != self && FactionHelper.isEntitiesHostile(self, receiver)) {
            // 原版比较的是 `== this.getMobType()`（凋灵 = UNDEAD），这里给一个必然不等的值
            return MobType.UNDEFINED;
        }
        return receiver.getMobType();
    }
}
