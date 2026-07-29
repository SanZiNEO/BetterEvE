package com.example.bettereve.scoreboard;

import com.example.bettereve.counter.DeathCounter;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "bettereve", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScoreboardManager {
    private static final String OBJECTIVE_NAME = "bettereve_deaths";
    private static final String OBJECTIVE_DISPLAY_NAME = "阵营死亡计数";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 已禁用 — 使用自定义 HUD 计分板 ScoreboardOverlay
        // 保留 clearScoreboard/refreshScoreboardImmediately 供指令使用
    }

    // 清除计分板数据
    public static void clearScoreboard(Level level) {
        if (level.isClientSide()) {
            return;
        }

        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            // 清除所有参与者的分数（包括旧名称和非阵营条目）
            for (String playerName : scoreboard.getTrackedPlayers()) {
                scoreboard.resetPlayerScore(playerName, objective);
            }
            
            // 强制刷新显示
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, null);
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
        }
    }

    // 添加一个立即刷新记分板的方法
    public static void refreshScoreboardImmediately(Level level) {
        if (level.isClientSide()) {
            return;
        }
        
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY, 
                    Component.literal(OBJECTIVE_DISPLAY_NAME), ObjectiveCriteria.RenderType.INTEGER);
        }
        
        // 立即更新所有阵营的分数为0
        for (Faction faction : FactionRegistry.getAllFactions()) {
            Score score = scoreboard.getOrCreatePlayerScore(faction.getName(), objective);
            score.setScore(0);
        }
        
        // 确保显示在侧边栏
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
    }
}