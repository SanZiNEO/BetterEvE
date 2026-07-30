package com.example.bettereve.ai;

import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.waypoint.WaypointManager;
import com.example.bettereve.waypoint.WaypointRegion;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.EnumSet;
import java.util.List;

/**
 * 寻路 AI — 使阵营实体沿着预设的路径点区域推进。
 * 
 * 推进规则：
 * 1. 先走自己阵营的路径点（从小到大）
 * 2. 走到终点后走敌方路径点（从大到小）
 * 3. 战斗优先于赶路（优先级低于战斗 Goal）
 */
public class PathfindToWaypointGoal extends Goal {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("BetterEvE/PathGoal");
    private static final String WAYPOINT_ORDER_KEY = "BetterEvE:LastWaypointOrder";

    private final PathfinderMob mob;
    private final PathNavigation navigation;
    private WaypointRegion currentTarget = null;
    private int currentOrder = 0;
    private boolean arrived = false;

    private static final int REPATH_INTERVAL = 80; // 4秒
    private int repathTimer = 0;

    public PathfindToWaypointGoal(PathfinderMob mob) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        String fid = FactionHelper.getEntityFactionId(mob);
        return fid != null && !"neutral".equals(fid);
    }

    @Override
    public boolean canContinueToUse() {
        if (arrived || mob.getTarget() != null) return false;
        return currentTarget != null && !navigation.isDone();
    }

    @Override
    public void start() {
        currentOrder = mob.getPersistentData().getInt(WAYPOINT_ORDER_KEY);
        arrived = false;
        checkCurrentPositionForWaypoint();
        selectNextTarget();
        LOGGER.info("[{}] 寻路开始 order={}", mob.getDisplayName().getString(), currentOrder);
    }

    @Override
    public void stop() {
        mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
        navigation.stop();
        LOGGER.info("[{}] 寻路停止 order={}", mob.getDisplayName().getString(), currentOrder);
    }

    @Override
    public void tick() {
        if (currentTarget == null) {
            selectNextTarget();
            return;
        }

        if (currentTarget.isInside(mob.getX(), mob.getZ())) {
            currentOrder = currentTarget.getOrder();
            mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
            int oldOrder = currentTarget.getOrder();
            selectNextTarget();
            LOGGER.info("[{}] 到达区域={} order={}->{}", mob.getDisplayName().getString(),
                currentTarget != null ? currentTarget.getId() : "终点", oldOrder,
                currentTarget != null ? currentTarget.getOrder() : "-");
            if (currentTarget != null) {
                navigateToFarthest(currentTarget);
                repathTimer = 0;
            }
            return;
        }

        repathTimer++;
        if (repathTimer >= REPATH_INTERVAL) {
            repathTimer = 0;
            navigateToFarthest(currentTarget);
        }
    }

    /** 导航到目标区域内离生物当前位置最远的角 */
    private void navigateToFarthest(WaypointRegion target) {
        double mx = mob.getX(), mz = mob.getZ();
        double[][] corners = {
            {target.getX1(), target.getZ1()}, {target.getX1(), target.getZ2()},
            {target.getX2(), target.getZ1()}, {target.getX2(), target.getZ2()}
        };
        double bestX = corners[0][0], bestZ = corners[0][1];
        double bestDistSq = 0;
        for (double[] c : corners) {
            double d = (c[0] - mx) * (c[0] - mx) + (c[1] - mz) * (c[1] - mz);
            if (d > bestDistSq) {
                bestDistSq = d;
                bestX = c[0];
                bestZ = c[1];
            }
        }
        LOGGER.info("[{}] 导航->区域={} order={} 目标=({},{})",
            mob.getDisplayName().getString(), target.getId(), target.getOrder(),
            String.format("%.0f", bestX), String.format("%.0f", bestZ));
        navigation.moveTo(bestX, mob.getY(), bestZ, 1.0);
    }

    private void checkCurrentPositionForWaypoint() {
        for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
            if (r.getOrder() > currentOrder && r.isInside(mob.getX(), mob.getZ())) {
                currentOrder = r.getOrder();
                mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
            }
        }
    }

    private void selectNextTarget() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) { arrived = true; return; }

        WaypointRegion next = WaypointManager.getNextWaypoint(factionId, currentOrder);
        if (next != null) {
            currentTarget = next;
            repathTimer = REPATH_INTERVAL;
            return;
        }

        int saved = currentOrder;
        currentOrder = Integer.MAX_VALUE;
        boolean found = findEnemyGoal();
        currentOrder = saved;
        if (found) return;

        arrived = true;
        currentTarget = null;
        LOGGER.info("[{}] 所有路径点已完成", mob.getDisplayName().getString());
    }

    private boolean findEnemyGoal() {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) return false;

        WaypointRegion best = null;
        for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
            if (factionId.equals(r.getFactionId())) continue;
            if (r.getOrder() < currentOrder && (best == null || r.getOrder() > best.getOrder())) {
                best = r;
            }
        }
        if (best != null) {
            currentTarget = best;
            repathTimer = REPATH_INTERVAL;
            return true;
        }
        return false;
    }
}
