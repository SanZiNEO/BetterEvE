package com.example.bettereve.scenario;

import com.example.bettereve.scenario.Scenario.KeepAliveZone;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 保活区域强制加载管理器。
 * <p>
 * 直播地图是纯虚空人工搭建、无自然刷怪，所有生物由模组指令管理；
 * 玩家视角不固定，生物可能在 simulation distance 之外 → chunk 卸载 → AI 冻结。
 * 因此剧本加载时把 keepAliveZone 覆盖的所有 chunk 用运行时 ticket 强制加载
 * （TicketType.FORCED，与原版 force chunk 同级别），保证区域内生物持续 tick（寻路/方块访问正常）。
 * <p>
 * 刻意不用 {@link ServerLevel#setChunkForced}：那是持久化数据（ForcedChunksSavedData，
 * 重启后仍生效）；本模组每次开播都重新加载剧本，改用运行时 region ticket，
 * 重启即清、加载剧本时重新绑定，不做任何存档。
 */
public class KeepAliveZoneManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/KeepAlive");
    private static final int FORCED_TICKET_LEVEL = 31; // 与原版 force chunk 相同级别

    private static KeepAliveZone boundZone = null;

    /** 绑定当前剧本的保活区域（先解绑旧区域） */
    public static void bind(ServerLevel level, KeepAliveZone zone) {
        unbind(level);
        if (zone == null || level == null) {
            return;
        }
        int minCX = (int) Math.floor(Math.min(zone.getX1(), zone.getX2())) >> 4;
        int maxCX = (int) Math.floor(Math.max(zone.getX1(), zone.getX2())) >> 4;
        int minCZ = (int) Math.floor(Math.min(zone.getZ1(), zone.getZ2())) >> 4;
        int maxCZ = (int) Math.floor(Math.max(zone.getZ1(), zone.getZ2())) >> 4;
        int count = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, FORCED_TICKET_LEVEL, pos);
                count++;
            }
        }
        boundZone = zone;
        LOGGER.info("保活区域强制加载：{} 个 chunk（x {}..{}, z {}..{}）", count, minCX, maxCX, minCZ, maxCZ);
    }

    /** 解绑当前保活区域的强制加载 ticket */
    public static void unbind(ServerLevel level) {
        if (boundZone == null || level == null) {
            boundZone = null;
            return;
        }
        int minCX = (int) Math.floor(Math.min(boundZone.getX1(), boundZone.getX2())) >> 4;
        int maxCX = (int) Math.floor(Math.max(boundZone.getX1(), boundZone.getX2())) >> 4;
        int minCZ = (int) Math.floor(Math.min(boundZone.getZ1(), boundZone.getZ2())) >> 4;
        int maxCZ = (int) Math.floor(Math.max(boundZone.getZ1(), boundZone.getZ2())) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, FORCED_TICKET_LEVEL, pos);
            }
        }
        boundZone = null;
    }

    /** 获取当前绑定的保活区域（调试/查询用） */
    public static KeepAliveZone getBoundZone() {
        return boundZone;
    }

    /** 获取服务端主世界（剧本加载在服务器线程调用；无服务器时返回 null） */
    public static ServerLevel getServerLevel() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.overworld() : null;
    }
}
