package com.sanzineo.bettereve.ai;

import com.sanzineo.bettereve.crystal.CrystalCollisionRegistry;
import com.sanzineo.bettereve.entity.CrystalEntity;
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
        // 原版明确不可通行的类型直接返回；注意不能把 OPEN 提前 return，
        // 因为基地是实体，它占用的方块通常是空气(OPEN)，必须继续走下面的基地碰撞箱检查。
        if (type == BlockPathTypes.BLOCKED
                || type == BlockPathTypes.WATER
                || type == BlockPathTypes.LAVA) {
            return type;
        }
        // 检查该坐标是否在基地碰撞箱内
        if (CrystalCollisionRegistry.isBlockedAt(x, z)) {
            // 只有“正在攻击的目标基地”才放行，其他基地（包括自己家基地）仍然算障碍。
            // 不能写成“有攻击目标就放行所有基地”，否则生物会把自己家基地也当空地走。
            if (this.mob != null && this.mob.getTarget() instanceof CrystalEntity targetCrystal) {
                String targetCrystalId = targetCrystal.getCrystalDefId();
                if (!targetCrystalId.isEmpty() && CrystalCollisionRegistry.isBlockedByCrystal(x, z, targetCrystalId)) {
                    return type;
                }
            }
            return BlockPathTypes.BLOCKED;
        }
        return type;
    }
}
