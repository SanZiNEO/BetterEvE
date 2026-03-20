package com.example.bettereve.entity;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 基地实体类
 * 代表游戏中的基地，具有攻击、防御和动画功能
 * 注意：基地实体不可移动，只能在原地攻击
 * 
 * 碰撞箱逻辑：
 * - 实体位置在碰撞箱底部（原版标准）
 * - 视线高度 = 碰撞箱高度（从底部到顶部的相对高度）
 * - 模型渲染在碰撞箱顶部（通过渲染器偏移实现）
 * - 其他生物攻击碰撞箱底部
 * - 远程攻击从模型位置发射
 */
public class BaseEntity extends Mob {
    
    // 动画状态枚举
    public enum AnimationState {
        STANDBY,    // 待机状态
        ATTACK,     // 攻击状态
        DEATH       // 死亡状态
    }
    
    // 实体数据访问器 - 用于网络同步
    private static final EntityDataAccessor<String> DATA_FACTION_ID = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_MAX_HEALTH = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ARMOR = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_DAMAGE = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ATTACK_RANGE = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ATTACK_COOLDOWN = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MODEL_SCALE = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ANIMATION_STATE = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_TARGET = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.INT);
    
    // 注意：碰撞箱已经通过EntityType.Builder.sized(width, height)预设
    // 实体位置在碰撞箱底部（原版标准）
    // 尺寸通过getDimensions()方法动态获取，不需要缓存

    // 攻击计时器 - 用于控制攻击频率
    private int attackTimer;
    
    // 动画播放计时器 - 用于计算动画进度
    private int animationTimer;
    
    // 当前动画状态
    private AnimationState currentAnimationState = AnimationState.STANDBY;
    
    // 客户端攻击动画相关
    @Nullable
    private LivingEntity clientSideCachedAttackTarget;
    private int clientSideAttackTime;
    private static final int BASE_ATTACK_DURATION = 80; // 基础攻击持续时间（ticks，4秒）
    private static final int BASE_ATTACK_COOLDOWN = 40; // 基础攻击冷却时间（ticks，2秒）
    private static final int BASE_TOTAL_CYCLE = 120; // 基础总攻击周期（ticks，6秒）

    public BaseEntity(EntityType<? extends BaseEntity> type, Level level) {
        super(type, level);
        this.attackTimer = 0;
        this.animationTimer = 0;
    }
    
    // 计算实际攻击动画持续时间（基于攻击周期比例）
    public int getAttackDuration() {
        float attackCycle = (float) this.getAttackCooldown(); // 攻击周期（秒）
        float baseCycle = 6.0f; // 基础周期（秒）
        float ratio = attackCycle / baseCycle;
        return (int) (BASE_ATTACK_DURATION * ratio);
    }
    
    // 计算实际攻击冷却时间（基于攻击周期比例）
    public int getAttackCooldownTicks() {
        float attackCycle = (float) this.getAttackCooldown(); // 攻击周期（秒）
        float baseCycle = 6.0f; // 基础周期（秒）
        float ratio = attackCycle / baseCycle;
        return (int) (BASE_ATTACK_COOLDOWN * ratio);
    }
    
    // 计算实际总攻击周期（ticks）
    public int getTotalCycleTicks() {
        return getAttackDuration() + getAttackCooldownTicks();
    }
    
    // 获取当前动画状态
    public AnimationState getAnimationState() {
        return currentAnimationState;
    }
    
    // 设置动画状态
    public void setAnimationState(AnimationState state) {
        if (this.currentAnimationState != state) {
            this.currentAnimationState = state;
            this.animationTimer = 0;
            this.entityData.set(DATA_ANIMATION_STATE, state.ordinal());
        }
    }
    
    // 获取动画播放进度 (0.0 到 ∞)
    public float getAnimationProgress(float partialTicks) {
        return (animationTimer + partialTicks) / 20.0F; // 20 tick = 1 秒
    }

    // 注册属性
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D) // 不可移动
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    // 初始化实体数据
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FACTION_ID, "neutral"); // 默认中立阵营
        this.entityData.define(DATA_MAX_HEALTH, 100);
        this.entityData.define(DATA_ARMOR, 0);
        this.entityData.define(DATA_ATTACK_DAMAGE, 10);
        this.entityData.define(DATA_ATTACK_RANGE, 32.0F);
        this.entityData.define(DATA_ATTACK_COOLDOWN, 2.0F);
        this.entityData.define(DATA_MODEL_SCALE, 1.0F);
        this.entityData.define(DATA_ANIMATION_STATE, AnimationState.STANDBY.ordinal());
        this.entityData.define(DATA_ATTACK_TARGET, 0);
    }
    
    // 注意：不需要缓存尺寸，原版Minecraft会自动处理碰撞箱尺寸

    // 阵营ID属性
    public void setFactionId(String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            factionId = "neutral";
        }
        this.entityData.set(DATA_FACTION_ID, factionId);
    }

    public String getFactionId() {
        String factionId = this.entityData.get(DATA_FACTION_ID);
        return factionId.isEmpty() ? "neutral" : factionId;
    }

    // 最大生命值属性
    public void setMaxHealth(int maxHealth) {
        this.entityData.set(DATA_MAX_HEALTH, maxHealth);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        this.setHealth(maxHealth);
    }

    public int getMaxHealthValue() {
        return this.entityData.get(DATA_MAX_HEALTH);
    }

    // 护甲属性
    public void setArmor(int armor) {
        this.entityData.set(DATA_ARMOR, armor);
        this.getAttribute(Attributes.ARMOR).setBaseValue(armor);
    }

    public int getArmorValue() {
        return this.entityData.get(DATA_ARMOR);
    }

    // 攻击伤害属性
    public void setAttackDamage(int attackDamage) {
        this.entityData.set(DATA_ATTACK_DAMAGE, attackDamage);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
    }

    public int getAttackDamageValue() {
        return this.entityData.get(DATA_ATTACK_DAMAGE);
    }

    // 攻击范围属性
    public void setAttackRange(double attackRange) {
        this.entityData.set(DATA_ATTACK_RANGE, (float) attackRange);
    }

    public double getAttackRange() {
        return this.entityData.get(DATA_ATTACK_RANGE);
    }

    // 攻击周期属性（总攻击时间，包括动画和冷却）
    public void setAttackCooldown(double attackCycle) {
        this.entityData.set(DATA_ATTACK_COOLDOWN, (float) attackCycle);
    }

    public double getAttackCooldown() {
        return this.entityData.get(DATA_ATTACK_COOLDOWN);
    }
    
    // 获取攻击周期的别名方法，更符合语义
    public double getAttackCycle() {
        return getAttackCooldown();
    }
    
    public void setAttackCycle(double attackCycle) {
        setAttackCooldown(attackCycle);
    }

    // 获取碰撞箱尺寸（从实体类型预设）
    public double getCollisionSize() {
        // 宽度是从中心到边缘的距离，所以实际碰撞箱宽度是宽度的一半
        EntityDimensions dimensions = this.getDimensions(Pose.STANDING);
        return dimensions.width / 2.0;
    }
    
    // 获取碰撞箱高度（从实体类型预设）
    public double getCollisionHeight() {
        EntityDimensions dimensions = this.getDimensions(Pose.STANDING);
        return dimensions.height;
    }
    
    // 获取实体类型名称中的尺寸信息
    public String getSizeKey() {
        String entityName = this.getType().getDescriptionId();
        if (entityName.contains("base_entity_")) {
            // 从实体名称中提取尺寸信息，如 "base_entity_3x10" -> "3x10"
            return entityName.substring(entityName.indexOf("base_entity_") + "base_entity_".length());
        }
        return "3x10"; // 默认尺寸
    }
    
    // 从尺寸键值解析宽度和高度
    public int[] parseSizeKey() {
        return BaseEntityType.parseSizeKey(getSizeKey());
    }
    
    // 注意：不需要缓存尺寸，原版Minecraft会自动处理

    // 模型缩放属性
    public void setModelScale(double modelScale) {
        this.entityData.set(DATA_MODEL_SCALE, (float) modelScale);
    }

    public double getModelScale() {
        return this.entityData.get(DATA_MODEL_SCALE);
    }

    // 视线高度设置
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
        // 视线高度 = 碰撞箱高度（从实体位置（底部）到碰撞箱顶部的相对高度）
        // 这样getEyeY()就在碰撞箱顶部
        return (float) this.getCollisionHeight();
    }
    
    /**
     * 获取远程攻击的出发点位置
     * 从模型位置发射（碰撞箱顶部 + 模型缩放）
     */
    public Vec3 getRangedAttackPosition() {
        // 偏移量 = 碰撞箱高度 + 模型缩放
        // 攻击从模型顶部发射（碰撞箱顶部上方）
        double offsetY = this.getCollisionHeight() + this.getModelScale();
        return new Vec3(this.getX(), this.getY() + offsetY, this.getZ());
    }
    
    // 注意：碰撞箱已经通过EntityType.Builder.sized(width, height)预设
    // 使用原版Minecraft的碰撞箱计算，不需要自定义方法

    // 初始化目标选择器
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new BaseEntityAttackGoal(this));
    }

    // 不可移动
    @Override
    public void travel(Vec3 vec3) {
        // 阻止实体移动
    }

    // 设置攻击目标
    public void setActiveAttackTarget(int targetId) {
        this.entityData.set(DATA_ATTACK_TARGET, targetId);
    }
    
    // 检查是否有攻击目标
    public boolean hasActiveAttackTarget() {
        return this.entityData.get(DATA_ATTACK_TARGET) != 0;
    }
    
    // 获取攻击目标
    @Nullable
    public LivingEntity getActiveAttackTarget() {
        if (!this.hasActiveAttackTarget()) {
            return null;
        } else if (this.level().isClientSide) {
            if (this.clientSideCachedAttackTarget != null) {
                return this.clientSideCachedAttackTarget;
            } else {
                Entity entity = this.level().getEntity(this.entityData.get(DATA_ATTACK_TARGET));
                if (entity instanceof LivingEntity) {
                    this.clientSideCachedAttackTarget = (LivingEntity) entity;
                    return this.clientSideCachedAttackTarget;
                } else {
                    return null;
                }
            }
        } else {
            return this.getTarget();
        }
    }
    
    // 同步数据更新时的处理
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> p_32834_) {
        super.onSyncedDataUpdated(p_32834_);
        if (DATA_ATTACK_TARGET.equals(p_32834_)) {
            this.clientSideAttackTime = 0;
            this.clientSideCachedAttackTarget = null;
        }
    }
    
    // 攻击逻辑 - 参考 Minecraft 原版 Guardian 类实现
    private void attackTarget(LivingEntity target) {
        try {
            if (target == null || !target.isAlive()) {
                return;
            }
            
            if (this.level().isClientSide) {
                return;
            }
            
            // 检查攻击范围
            double distance = this.distanceTo(target);
            if (distance > this.getAttackRange()) {
                return;
            }
            
            // 执行混合伤害攻击（最大化穿透）
            float totalDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            
            // 伤害类型分配：
            // 60% 凋零伤害（穿透护甲）
            // 30% 魔法伤害（穿透抗性）
            // 10% 龙息伤害（特殊穿透）
            float witherDamage = totalDamage * 0.6f;
            float magicDamage = totalDamage * 0.3f;
            float dragonBreathDamage = totalDamage * 0.1f;
            
            // 确保伤害值为正数
            witherDamage = Math.max(0.01f, witherDamage);
            magicDamage = Math.max(0.01f, magicDamage);
            dragonBreathDamage = Math.max(0.01f, dragonBreathDamage);
            
            // 依次造成伤害
            if (witherDamage > 0) {
                target.hurt(this.damageSources().wither(), witherDamage);
            }
            
            if (magicDamage > 0) {
                target.hurt(this.damageSources().magic(), magicDamage);
            }
            
            if (dragonBreathDamage > 0) {
                target.hurt(this.damageSources().dragonBreath(), dragonBreathDamage);
            }
            
            // 播放攻击音效
            this.playSound(this.getAttackSound(), 1.0F, 1.0F);
        } catch (Exception e) {
            System.err.println("BaseEntity attackTarget error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 实体更新
    @Override
    public void tick() {
        super.tick();

        // 更新攻击计时器
        if (this.attackTimer > 0) {
            this.attackTimer--;
        }
        
        // 更新动画计时器
        this.animationTimer++;
        
        // 客户端攻击动画更新
        if (this.level().isClientSide) {
            if (this.hasActiveAttackTarget()) {
                if (this.clientSideAttackTime < getAttackDuration()) {
                    ++this.clientSideAttackTime;
                }
                
                // 每tick生成激光粒子（参考Guardian实现）
                this.tickAttackParticles();
            }
        }
        
        // 根据实体状态自动更新动画
        if (this.isDeadOrDying()) {
            this.setAnimationState(AnimationState.DEATH);
        } else if (this.hasActiveAttackTarget() || this.attackTimer > 0) {
            // 如果有攻击目标或正在攻击冷却，显示攻击动画
            this.setAnimationState(AnimationState.ATTACK);
        } else {
            this.setAnimationState(AnimationState.STANDBY);
        }
    }
    
    // 获取攻击动画进度
    public float getAttackAnimationScale(float partialTicks) {
        return ((float) this.clientSideAttackTime + partialTicks) / (float) getAttackDuration();
    }
    
    // 获取客户端攻击时间
    public float getClientSideAttackTime() {
        return (float) this.clientSideAttackTime;
    }
    
    // 获取当前攻击进度（0.0 到 1.0）
    public float getAttackProgress() {
        if (!this.hasActiveAttackTarget() || this.clientSideAttackTime <= 0) {
            return 0.0f;
        }
        return Math.min((float) this.clientSideAttackTime / (float) getAttackDuration(), 1.0f);
    }
    
    // 获取动画速度因子
    public float getAnimationSpeedFactor() {
        if (this.isDeadOrDying()) {
            return 4.0f; // 死亡：4倍速
        }
        
        if (this.hasActiveAttackTarget()) {
            float attackProgress = getAttackProgress();
            float chargingRatio = 0.8f; // 蓄力阶段占80%
            
            if (attackProgress < chargingRatio) {
                // 蓄力阶段：从1.0线性过渡到2.0
                float chargingProgress = attackProgress / chargingRatio;
                return 1.0f + chargingProgress * 1.0f; // 1.0 → 2.0
            } else {
                // 攻击阶段：2.0倍速
                return 2.0f;
            }
        }
        
        // 待机状态：1.0倍速
        return 1.0f;
    }

    // 读取 NBT 数据
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BetterEvE:FactionId")) {
            this.setFactionId(tag.getString("BetterEvE:FactionId"));
        }
        if (tag.contains("BetterEvE:MaxHealth")) {
            this.setMaxHealth(tag.getInt("BetterEvE:MaxHealth"));
        }
        if (tag.contains("BetterEvE:Armor")) {
            this.setArmor(tag.getInt("BetterEvE:Armor"));
        }
        if (tag.contains("BetterEvE:AttackDamage")) {
            this.setAttackDamage(tag.getInt("BetterEvE:AttackDamage"));
        }
        if (tag.contains("BetterEvE:AttackRange")) {
            this.setAttackRange(tag.getDouble("BetterEvE:AttackRange"));
        }
        if (tag.contains("BetterEvE:AttackCooldown")) {
            this.setAttackCooldown(tag.getDouble("BetterEvE:AttackCooldown"));
        }
        if (tag.contains("BetterEvE:ModelScale")) {
            this.setModelScale(tag.getDouble("BetterEvE:ModelScale"));
        }
        if (tag.contains("BetterEvE:AnimationState")) {
            int stateOrdinal = tag.getInt("BetterEvE:AnimationState");
            if (stateOrdinal >= 0 && stateOrdinal < AnimationState.values().length) {
                this.setAnimationState(AnimationState.values()[stateOrdinal]);
            }
        }
    }

    // 写入 NBT 数据
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("BetterEvE:FactionId", this.getFactionId());
        tag.putInt("BetterEvE:MaxHealth", this.getMaxHealthValue());
        tag.putInt("BetterEvE:Armor", this.getArmorValue());
        tag.putInt("BetterEvE:AttackDamage", this.getAttackDamageValue());
        tag.putDouble("BetterEvE:AttackRange", this.getAttackRange());
        tag.putDouble("BetterEvE:AttackCooldown", this.getAttackCooldown());
        tag.putDouble("BetterEvE:ModelScale", this.getModelScale());
        tag.putInt("BetterEvE:AnimationState", this.currentAnimationState.ordinal());
    }

    // 初始化时设置属性
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (tag != null) {
            this.readAdditionalSaveData(tag);
        }
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.getMaxHealthValue());
        this.getAttribute(Attributes.ARMOR).setBaseValue(this.getArmorValue());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(this.getAttackDamageValue());
        this.setHealth(this.getMaxHealthValue());
        return result;
    }


    // 获取攻击音效
    protected SoundEvent getAttackSound() {
        return SoundEvents.GENERIC_EXPLODE;
    }

    // 重写视线检查方法，实现视线穿墙
    @Override
    public boolean hasLineOfSight(Entity entity) {
        // 基地视线穿墙，总是返回 true，表示能看到目标
        return true;
    }
    
    // 重写 isInWall 方法，确保基地实体永远不被判定为完全被方块包围
    @Override
    public boolean isInWall() {
        // 基地实体总是可见，不被方块包围判定影响
        return false;
    }
    
    // 重写 isInvisible 方法，确保基地实体始终可见
    @Override
    public boolean isInvisible() {
        return false;
    }
    
    // 重写 isInvisibleTo 方法，确保基地实体对所有玩家可见
    @Override
    public boolean isInvisibleTo(Player player) {
        return false;
    }

    // 攻击目标选择器 - 参考 Minecraft 原版 Guardian 类实现
    private static class BaseEntityAttackGoal extends Goal {
        private final BaseEntity entity;
        private LivingEntity target;
        private int attackTime;

        public BaseEntityAttackGoal(BaseEntity entity) {
            this.entity = entity;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET, Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.entity.getFactionId().isEmpty()) {
                return false;
            }

            double range = this.entity.getAttackRange();
            List<LivingEntity> nearbyEntities = this.entity.level().getEntitiesOfClass(
                    LivingEntity.class,
                    this.entity.getBoundingBox().inflate(range),
                    targetEntity -> targetEntity != this.entity &&
                            targetEntity.isAlive() &&
                            !targetEntity.isInvulnerable() &&
                            !targetEntity.isDeadOrDying() &&
                            EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(targetEntity) &&
                            FactionHelper.isEntitiesHostile(this.entity, targetEntity)
            );

            if (!nearbyEntities.isEmpty()) {
                this.target = nearbyEntities.get(0);
                this.attackTime = -10; // 参考Guardian，从-10开始
                return true;
            }

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.target.isAlive() &&
                    this.entity.distanceTo(this.target) <= this.entity.getAttackRange() &&
                    FactionHelper.isEntitiesHostile(this.entity, this.target);
        }

        @Override
        public void start() {
            this.entity.setTarget(this.target);
            this.attackTime = -10; // 参考Guardian，从-10开始
        }

        @Override
        public void stop() {
            this.entity.setTarget(null);
            this.entity.setActiveAttackTarget(0);
            this.target = null;
            this.attackTime = -10;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.target != null && this.target.isAlive()) {
                this.entity.getLookControl().setLookAt(this.target, 90.0F, 90.0F);
                
                // 攻击时序控制 - 参考Guardian实现
                this.attackTime++;
                
                // 开始攻击时设置攻击目标ID
                if (this.attackTime == 0) {
                    this.entity.setActiveAttackTarget(this.target.getId());
                    if (!this.entity.isSilent()) {
                        this.entity.level().broadcastEntityEvent(this.entity, (byte)21);
                    }
                } else if (this.attackTime >= this.entity.getAttackDuration()) {
                    // 攻击完成，造成伤害
                    this.entity.attackTarget(this.target);
                    
                    // 攻击完成后清除目标，让canUse()重新选择
                    this.entity.setTarget(null);
                    this.entity.setActiveAttackTarget(0);
                    this.target = null;
                    this.attackTime = -10;
                }
            }
        }
    }
    
    // 每tick生成激光粒子（参考Guardian实现）
    private void tickAttackParticles() {
        // 获取攻击目标
        LivingEntity target = this.getActiveAttackTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // 计算攻击目标的位置（每tick重新计算，实现实时追踪）
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.5D) - this.getEyeY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
        d0 /= d3;
        d1 /= d3;
        d2 /= d3;
        
        // 生成连续的粒子效果，组成激光路径
        // 固定粒子间距0.5
        double particleSpacing = 0.05D;
        double d4 = 0.0D;
        
        // 创建DUST粒子选项：白色激光，五分之一大小（0.2f）
        // 颜色：Vector3f(1.0f, 1.0f, 1.0f) = 白色
        DustParticleOptions dustOptions = new DustParticleOptions(
            new Vector3f(1.0f, 1.0f, 1.0f),  // RGB颜色（白色）
            1.0f  // 缩放大小，一倍
        );
        
        while(d4 < d3) {
            // 固定间距0.5
            d4 += particleSpacing;
            
            // 使用DUST粒子，支持颜色和大小控制
            // DUST粒子最后三个参数是速度，我们设为0
            this.level().addParticle(dustOptions, 
                    this.getX() + d0 * d4, 
                    this.getEyeY() + d1 * d4, 
                    this.getZ() + d2 * d4, 
                    0.0D, 0.0D, 0.0D);  // 速度设为0
        }
    }
}
