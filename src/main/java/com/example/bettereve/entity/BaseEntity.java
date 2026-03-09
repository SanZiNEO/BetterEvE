package com.example.bettereve.entity;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 基地实体类
 * 代表游戏中的基地，具有攻击、防御和动画功能
 * 注意：基地实体不可移动，只能在原地攻击
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
    private static final EntityDataAccessor<Float> DATA_COLLISION_SIZE = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_COLLISION_HEIGHT = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MODEL_SCALE = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ANIMATION_STATE = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_TARGET = SynchedEntityData.defineId(BaseEntity.class, EntityDataSerializers.INT);

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
        this.entityData.define(DATA_COLLISION_SIZE, 3.0F);
        this.entityData.define(DATA_COLLISION_HEIGHT, 10.0F);
        this.entityData.define(DATA_MODEL_SCALE, 1.0F);
        this.entityData.define(DATA_ANIMATION_STATE, AnimationState.STANDBY.ordinal());
        this.entityData.define(DATA_ATTACK_TARGET, 0);
    }

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

    // 碰撞箱大小属性
    public void setCollisionSize(double collisionSize) {
        this.entityData.set(DATA_COLLISION_SIZE, (float) collisionSize);
        this.refreshDimensions();
    }

    public double getCollisionSize() {
        return this.entityData.get(DATA_COLLISION_SIZE);
    }

    // 碰撞箱高度属性
    public void setCollisionHeight(double collisionHeight) {
        this.entityData.set(DATA_COLLISION_HEIGHT, (float) collisionHeight);
        this.refreshDimensions();
    }

    public double getCollisionHeight() {
        return this.entityData.get(DATA_COLLISION_HEIGHT);
    }

    // 模型缩放属性
    public void setModelScale(double modelScale) {
        this.entityData.set(DATA_MODEL_SCALE, (float) modelScale);
    }

    public double getModelScale() {
        return this.entityData.get(DATA_MODEL_SCALE);
    }

    // 设置尺寸
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
        return size.height;
    }

    // 重写 bounding box 计算，实现从顶部向下计算
    @Override
    protected AABB makeBoundingBox() {
        float collisionSize = (float) this.getCollisionSize();
        float collisionHeight = (float) this.getCollisionHeight();
        
        // 碰撞箱计算方式：使用原版方法，但高为负值，实现从顶部向下
        // 原版 Minecraft 碰撞箱是从底部向上计算，使用负值表示高就会向下
        float halfWidth = collisionSize;
        double x = this.getX();
        double y = this.getY(); // 保持原版原点位置（底部）
        double z = this.getZ();
        
        return new AABB(
            x - halfWidth, y, z - halfWidth,
            x + halfWidth, y - collisionHeight, z + halfWidth
        );
    }
    
    @Override
    public AABB getBoundingBoxForPose(Pose p_20218_) {
        return this.makeBoundingBox();
    }
    
    @Override
    public void refreshDimensions() {
        // 直接重新计算 bounding box，不使用原版尺寸更新逻辑
        this.reapplyPosition();
    }

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
            
            // 检查攻击冷却
            if (this.attackTimer > 0) {
                return;
            }
            
            // 设置攻击冷却（使用计算后的冷却时间）
            this.attackTimer = this.getAttackCooldownTicks();
            
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
            
            // 设置攻击动画
            this.setAnimationState(AnimationState.ATTACK);
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
            }
        }
        
        // 根据实体状态自动更新动画
        if (this.isDeadOrDying()) {
            this.setAnimationState(AnimationState.DEATH);
        } else if (this.attackTimer > 0) {
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
        if (tag.contains("BetterEvE:CollisionSize")) {
            this.setCollisionSize(tag.getDouble("BetterEvE:CollisionSize"));
        }
        if (tag.contains("BetterEvE:CollisionHeight")) {
            this.setCollisionHeight(tag.getDouble("BetterEvE:CollisionHeight"));
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
        tag.putDouble("BetterEvE:CollisionSize", this.getCollisionSize());
        tag.putDouble("BetterEvE:CollisionHeight", this.getCollisionHeight());
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
            if (this.entity.getFactionId().isEmpty() || this.entity.attackTimer > 0) {
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
                            FactionHelper.isEntitiesHostile(this.entity, targetEntity)
            );

            if (!nearbyEntities.isEmpty()) {
                this.target = nearbyEntities.get(0);
                this.attackTime = 0;
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
            this.attackTime = 0;
        }

        @Override
        public void stop() {
            this.entity.setTarget(null);
            this.entity.setActiveAttackTarget(0);
            this.target = null;
            this.attackTime = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.target != null && this.target.isAlive()) {
                this.entity.getLookControl().setLookAt(this.target, 90.0F, 90.0F);
                
                // 攻击时序控制
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
                    this.attackTime = 0;
                    this.entity.setActiveAttackTarget(0);
                    
                    // 设置攻击冷却
                    this.entity.attackTimer = this.entity.getAttackCooldownTicks();
                }
            }
        }
    }
}