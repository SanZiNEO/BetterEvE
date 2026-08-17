package com.example.bettereve.waypoint;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 兵线进度全局注册表（方案 B）。
 * <p>
 * 全局管理每个生物的阵营兵线进度（阶段 + 已到达 order），供 PathfindToWaypointGoal 读写，
 * 目标是直播场景的稳定性：生物被战斗拖离路线、被打断重启、甚至卸载重生成后，
 * 进度不丢失、不走回头路。剧本重载时整体清除。
 * <p>
 * 生命周期：
 * - 生物首次寻路时 getOrCreate 创建；
 * - 生物换阵营时 getOrCreate 自动重置（新阵营从头走）；
 * - 实体离开世界（死亡/卸载）时由 TargetingHandler 的 EntityLeaveWorldEvent 清理；
 * - 剧本重载时由 WaypointManager.loadFromScenario 调用 clearAll 整体清除。
 */
public class WaypointProgressRegistry {
    private static final Map<UUID, Progress> PROGRESS = new HashMap<>();

    /** 单个生物的兵线进度 */
    public static class Progress {
        /** 进度归属阵营（与当前阵营不一致即视为过期，重建） */
        public final String factionId;
        /** 绑定的路线 id（null = 尚未绑定，首次寻路时从 NBT 或就近选择） */
        public String routeId;
        /** 当前已推进到的 route 计划下标（下一个要去的 waypoint 是 plan.get(nextIndex)） */
        public int nextIndex;
        /** 全部走完（线性终点） */
        public boolean finished;
        /** 最后活跃的游戏 tick（清理用） */
        public long lastActiveTick;

        public Progress(String factionId, long tick) {
            this.factionId = factionId;
            this.lastActiveTick = tick;
        }

        @Override
        public String toString() {
            return "Progress{" + factionId + ", route=" + routeId
                    + ", nextIndex=" + nextIndex + ", finished=" + finished + "}";
        }
    }

    /**
     * 获取生物的进度；不存在或阵营已变化 → 创建新的（旧进度作废，从头走）。
     * 同时刷新活跃时间戳。
     */
    public static Progress getOrCreate(Mob mob) {
        UUID uuid = mob.getUUID();
        String fid = FactionHelper.getEntityFactionId(mob);
        long tick = mob.level().getGameTime();
        Progress p = PROGRESS.get(uuid);
        if (p == null || !fid.equals(p.factionId)) {
            p = new Progress(fid, tick);
            PROGRESS.put(uuid, p);
        }
        p.lastActiveTick = tick;
        return p;
    }

    /** 移除指定实体的进度（实体死亡/卸载时调用） */
    public static void remove(Mob mob) {
        PROGRESS.remove(mob.getUUID());
    }

    /** 剧本重载时清除全部进度 */
    public static void clearAll() {
        PROGRESS.clear();
    }

    /** 定期清理：清除超过 maxIdleTicks 未活跃的条目（实体已卸载且事件漏清理的兜底） */
    public static void cleanup(long currentTick, long maxIdleTicks) {
        Iterator<Map.Entry<UUID, Progress>> it = PROGRESS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Progress> e = it.next();
            if (currentTick - e.getValue().lastActiveTick > maxIdleTicks) {
                it.remove();
            }
        }
    }

    /** 全部进度快照（调试/可视化用） */
    public static Map<UUID, Progress> snapshot() {
        return new HashMap<>(PROGRESS);
    }
}
