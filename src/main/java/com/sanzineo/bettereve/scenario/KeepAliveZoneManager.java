package com.sanzineo.bettereve.scenario;

import com.sanzineo.bettereve.Bettereve;
import com.sanzineo.bettereve.scenario.Scenario.KeepAliveZone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 保活区域强制加载管理器（分批加载版）。
 * <p>
 * 直播地图是纯虚空人工搭建、无自然刷怪，所有生物由模组指令管理；
 * 玩家视角不固定，生物可能在 simulation distance 之外 → chunk 卸载 → AI 冻结。
 * 因此剧本加载时把 keepAliveZone 覆盖的所有 chunk 用运行时 ticket 强制加载
 * （TicketType.FORCED，与原版 force chunk 同级别），保证区域内生物持续 tick。
 * <p>
 * 为避免“一次性压入 32+ 个 chunk 的加载 ticket”导致瞬间卡顿，这里改成：
 * - 每 tick 只加 {@link #BATCH_SIZE} 个 ticket，分批加载；
 * - 如果新区域和当前已绑定区域相同，直接跳过，不做 unbind/rebind；
 * - 提供 preload 入口，方便以后在服务器启动阶段提前分批加载固定地图区域。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KeepAliveZoneManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/KeepAlive");
    private static final int FORCED_TICKET_LEVEL = 31; // 与原版 force chunk 相同级别
    private static final int BATCH_SIZE = 4; // 每 tick 最多新增的 ticket 数量

    /** 当前已经完全绑定完成的区域 */
    private static KeepAliveZone boundZone = null;
    /** 当前已实际添加 ticket 的 chunk（用于解绑/取消） */
    private static final List<ChunkPos> activeChunks = new ArrayList<>();

    /** 正在分批绑定中的目标区域与待加载 chunk */
    private static KeepAliveZone pendingZone = null;
    private static ServerLevel pendingLevel = null;
    private static final List<ChunkPos> pendingChunks = new ArrayList<>();

    /** 绑定当前剧本的保活区域（同区域跳过，新区域分批加载） */
    public static void bind(ServerLevel level, KeepAliveZone zone) {
        if (zone == null || level == null) {
            unbind(level);
            return;
        }

        // 同区域跳过：不重复 unbind/rebind，避免 chunk 抖动
        if ((sameZone(boundZone, zone) && pendingZone == null) || sameZone(pendingZone, zone)) {
            return;
        }

        // 取消/解绑旧的绑定（包括未完成的分批）
        unbind(level);

        // 计算新区域覆盖的 chunk，进入分批队列
        pendingLevel = level;
        pendingZone = zone;
        pendingChunks.clear();
        int minCX = (int) Math.floor(Math.min(zone.getX1(), zone.getX2())) >> 4;
        int maxCX = (int) Math.floor(Math.max(zone.getX1(), zone.getX2())) >> 4;
        int minCZ = (int) Math.floor(Math.min(zone.getZ1(), zone.getZ2())) >> 4;
        int maxCZ = (int) Math.floor(Math.max(zone.getZ1(), zone.getZ2())) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                pendingChunks.add(new ChunkPos(cx, cz));
            }
        }
        LOGGER.info("保活区域分批加载：{} 个 chunk 排队（每 tick {} 个）", pendingChunks.size(), BATCH_SIZE);
    }

    /** 可选：服务器启动/固定地图时提前分批加载某个区域（内部走同一套分批逻辑） */
    public static void preload(ServerLevel level, KeepAliveZone zone) {
        bind(level, zone);
    }

    /** 解绑当前保活区域的强制加载 ticket */
    public static void unbind(ServerLevel level) {
        // 取消未完成的分批
        pendingChunks.clear();
        pendingZone = null;
        pendingLevel = null;

        // 移除所有已添加的 ticket
        if (level != null) {
            for (ChunkPos pos : activeChunks) {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, FORCED_TICKET_LEVEL, pos);
            }
        }
        activeChunks.clear();
        boundZone = null;
    }

    /** 服务器 tick：分批把待加载 chunk 的 ticket 加进去 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (pendingChunks.isEmpty()) {
            if (pendingZone != null) {
                // 所有 ticket 已加完，正式标记绑定完成
                boundZone = pendingZone;
                pendingZone = null;
                pendingLevel = null;
            }
            return;
        }

        ServerLevel level = pendingLevel != null ? pendingLevel : getServerLevel();
        if (level == null) {
            return;
        }

        int count = Math.min(BATCH_SIZE, pendingChunks.size());
        for (int i = 0; i < count; i++) {
            ChunkPos pos = pendingChunks.remove(0);
            level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, FORCED_TICKET_LEVEL, pos);
            activeChunks.add(pos);
        }

        if (pendingChunks.isEmpty()) {
            boundZone = pendingZone;
            pendingZone = null;
            pendingLevel = null;
            LOGGER.info("保活区域分批加载完成：共 {} 个 chunk", activeChunks.size());
        }
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

    private static boolean sameZone(KeepAliveZone a, KeepAliveZone b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Double.doubleToLongBits(a.getX1()) == Double.doubleToLongBits(b.getX1())
                && Double.doubleToLongBits(a.getZ1()) == Double.doubleToLongBits(b.getZ1())
                && Double.doubleToLongBits(a.getX2()) == Double.doubleToLongBits(b.getX2())
                && Double.doubleToLongBits(a.getZ2()) == Double.doubleToLongBits(b.getZ2());
    }
}
