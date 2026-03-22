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
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        Player player = event.player;
        Scoreboard scoreboard = player.getScoreboard();

        // 检查目标是否存在，不存在则创建
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY, 
                    Component.literal(OBJECTIVE_DISPLAY_NAME), ObjectiveCriteria.RenderType.INTEGER);
        }

        // 确保玩家的计分板上显示这个目标
        // 简化逻辑，不尝试添加到不存在的团队

        // 更新每个阵营的死亡计数
        String worldId = player.level().dimension().location().toString();
        DeathCounter counter = DeathCounter.getInstance(player.level());

        for (Faction faction : FactionRegistry.getAllFactions()) {
            int deaths = counter.getTotalDeathsByFaction(worldId, faction.getId());
            // 使用阵营名称作为显示名称
            String displayName = faction.getName();
            
            // 更新计分板
            Score score = scoreboard.getOrCreatePlayerScore(displayName, objective);
            score.setScore(deaths);
        }

        // 设置目标显示在侧边栏
        if (scoreboard.getDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR) != objective) {
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
        }
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