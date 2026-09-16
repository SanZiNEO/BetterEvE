package com.example.bettereve.faction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 统一的"找出一个实体属于谁"解析器。
 *
 * 存在的理由：投掷物继承与召唤物继承本质是同一个问题 ——
 * "这个实体从谁那里来的"。原先两处各写一套，规则容易走偏。
 *
 * 解析顺序（命中即停，全部落空返回 null）：
 *   1. TraceableEntity.getOwner()  —— 原版投掷物、尖牙、恼鬼都实现该接口
 *   2. OwnableEntity.getOwner()    —— 可驯服生物、从者体系
 *   3. OwnableEntity.getOwnerUUID() 反查 —— owner 未加载到本维度时
 *   4. NBT 召唤者痕迹（见 SummonedMarker）—— 自己起键名的模组
 *
 * 不做任何反射，不做任何范围扫描。
 */
public final class SummonerResolver {

    private SummonerResolver() {
    }

    /**
     * 解析实体的主人/召唤者。
     *
     * @return 主人实体，找不到返回 null
     */
    @Nullable
    public static Entity resolve(Entity entity) {
        if (entity == null) {
            return null;
        }

        // 1. TraceableEntity
        if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            if (owner != null) {
                return owner;
            }
        }

        // 2/3. OwnableEntity（含 UUID 反查）
        if (entity instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner != null) {
                return owner;
            }
            UUID ownerUuid = ownable.getOwnerUUID();
            if (ownerUuid != null && entity.level() instanceof ServerLevel serverLevel) {
                Entity resolved = serverLevel.getEntity(ownerUuid);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        // 4. NBT 痕迹
        UUID nbtUuid = SummonedMarker.resolveSummonerUuid(entity);
        if (nbtUuid != null && entity.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(nbtUuid);
        }

        return null;
    }

    /**
     * 只要 owner 实体（不要 UUID 反查与 NBT 痕迹），供"投掷物即时继承"这类
     * 需要在生成当刻就判定、且不应依赖延迟解析的场景使用。
     */
    @Nullable
    public static Entity resolveDirectOwner(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            if (owner != null) {
                return owner;
            }
        }
        if (entity instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner != null) {
                return owner;
            }
            UUID ownerUuid = ownable.getOwnerUUID();
            if (ownerUuid != null && entity.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getEntity(ownerUuid);
            }
        }
        return null;
    }

    /**
     * 多部件生物的父链上溯：沿 ParentUUID 逐节找到主体（尾 → 身 → 头）。
     * 带深度上限防止环状链接死循环。
     */
    public static LivingEntity resolveMultipartRoot(LivingEntity piece, int maxDepth) {
        LivingEntity current = piece;
        for (int depth = 0; depth < maxDepth; depth++) {
            UUID parentUuid = SummonedMarker.getParentLinkUuid(current);
            if (parentUuid == null || !(current.level() instanceof ServerLevel serverLevel)) {
                return current;
            }
            Entity parent = serverLevel.getEntity(parentUuid);
            if (!(parent instanceof LivingEntity parentLiving) || parentLiving == current) {
                return current;
            }
            current = parentLiving;
        }
        return current;
    }
}
