package com.example.bettereve.waypoint;

import com.example.bettereve.Bettereve;
import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局寻路扫描 — 每秒检测一次生物是否到达路径点区域，到达则标记完成并派发下一个。
 * 不在区域内时不做任何操作，让生物自己走原版寻路。
 * 攻击中的生物（getTarget() != null）跳过，不干扰战斗。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaypointPathfindingHandler {
    private static final int INTERVAL = 20;
    private static final String WAYPOINT_ORDER_KEY = "BetterEvE:LastWaypointOrder";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var level : server.getAllLevels()) {
            if (level.getGameTime() % INTERVAL != 0) continue;

            // 收集所有带阵营的 PathfinderMob
            List<Mob> mobs = new ArrayList<>();
            for (var entity : level.getEntities().getAll()) {
                if (entity instanceof PathfinderMob pm) {
                    String fid = FactionHelper.getEntityFactionId(pm);
                    if (fid != null && !"neutral".equals(fid)) {
                        mobs.add(pm);
                    }
                }
            }

            for (Mob mob : mobs) {
                if (mob.getTarget() != null) continue; // 正在攻击，跳过
                processMob((PathfinderMob) mob);
            }
        }
    }

    private static void processMob(PathfinderMob mob) {
        String factionId = FactionHelper.getEntityFactionId(mob);
        if (factionId == null) return;

        int currentOrder = mob.getPersistentData().getInt(WAYPOINT_ORDER_KEY);
        boolean arrived = false;

        // 检查当前是否在某个路径点区域内（踏入边界就算到达）
        for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
            if (r.getOrder() > currentOrder && r.isInside(mob.getX(), mob.getZ())) {
                currentOrder = r.getOrder();
                arrived = true;
            }
        }

        if (arrived) {
            mob.getPersistentData().putInt(WAYPOINT_ORDER_KEY, currentOrder);
            WaypointRegion next = findNextRegion(factionId, currentOrder);
            if (next != null) navigateToFarthest(mob, next);
            return;
        }

        // 没有当前目标或导航已完成，派发下一个
        if (mob.getNavigation().isDone()) {
            WaypointRegion next = findNextRegion(factionId, currentOrder);
            if (next != null) navigateToFarthest(mob, next);
        }
    }

    /** 导航到区域最远角，让生物走穿整个区域 */
    private static void navigateToFarthest(PathfinderMob mob, WaypointRegion target) {
        double mx = mob.getX(), mz = mob.getZ();
        double[][] corners = {
            {target.getX1(), target.getZ1()}, {target.getX1(), target.getZ2()},
            {target.getX2(), target.getZ1()}, {target.getX2(), target.getZ2()}
        };
        double bestX = corners[0][0], bestZ = corners[0][1];
        double bestDistSq = -1;
        for (double[] c : corners) {
            double d = (c[0] - mx) * (c[0] - mx) + (c[1] - mz) * (c[1] - mz);
            if (d > bestDistSq) {
                bestDistSq = d;
                bestX = c[0];
                bestZ = c[1];
            }
        }
        mob.getNavigation().moveTo(bestX, mob.getY(), bestZ, 1.0);
    }

    private static WaypointRegion findNextRegion(String factionId, int currentOrder) {
        List<WaypointRegion> all = WaypointManager.getAllWaypoints();
        WaypointRegion best = null;

        // 1. 己方 order > currentOrder 的最小值（从小到大）
        for (WaypointRegion r : all) {
            if (!factionId.equals(r.getFactionId())) continue;
            if (r.getOrder() > currentOrder && (best == null || r.getOrder() < best.getOrder())) {
                best = r;
            }
        }
        if (best != null) return best;

        // 2. 己方走完，找敌方（从大到小）
        int threshold = hasOwnRemaining(factionId, currentOrder) ? currentOrder : Integer.MAX_VALUE;
        best = null;
        for (WaypointRegion r : all) {
            if (factionId.equals(r.getFactionId())) continue;
            if (r.getOrder() < threshold && (best == null || r.getOrder() > best.getOrder())) {
                best = r;
            }
        }
        return best;
    }

    private static boolean hasOwnRemaining(String factionId, int currentOrder) {
        for (WaypointRegion r : WaypointManager.getAllWaypoints()) {
            if (factionId.equals(r.getFactionId()) && r.getOrder() > currentOrder) return true;
        }
        return false;
    }
}
