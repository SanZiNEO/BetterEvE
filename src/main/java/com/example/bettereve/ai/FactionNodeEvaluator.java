package com.example.bettereve.ai;

import com.example.bettereve.crystal.CrystalCollisionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * 自定义节点评估器 — 在 WalkNodeEvaluator 基础上增加基地碰撞箱检测。
 * A* 寻路时将基地占用的方块标记为 BLOCKED，使路径自然绕过基地。
 */
public class FactionNodeEvaluator extends WalkNodeEvaluator {

    public FactionNodeEvaluator() {
        super();
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z);
        // 如果原版已经标记为不可通行，直接返回
        if (type == BlockPathTypes.BLOCKED || type == BlockPathTypes.WATER
                || type == BlockPathTypes.LAVA || type == BlockPathTypes.OPEN) {
            return type;
        }
        // 检查该坐标是否在基地碰撞箱内
        if (CrystalCollisionRegistry.isBlockedAt(x, z)) {
            // 攻击模式下（有攻击目标）放行基地：寻路时绕基地，但追击攻击目标时允许靠近
            if (this.mob != null && this.mob.getTarget() != null) {
                return type;
            }
            return BlockPathTypes.BLOCKED;
        }
        return type;
    }
}
