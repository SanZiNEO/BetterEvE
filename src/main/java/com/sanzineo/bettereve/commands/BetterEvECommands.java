package com.sanzineo.bettereve.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * `/bettereve` 命令树的唯一入口：只负责组装根命令，
 * 各子树分别由同包下的职责类提供。
 */
@Mod.EventBusSubscriber(modid = BetterEvECommands.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BetterEvECommands {

    public static final String MODID = "bettereve";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("bettereve")
            .then(FactionCommands.faction())
            .then(ScenarioCommands.scenario())
            .then(ScenarioCommands.phase())
            .then(WaypointCommands.waypoint())
            .then(CrystalCommands.crystal())
            .then(SpawnCommands.spawn())
            .then(ScoreboardCommands.scoreboard())
            .then(ScoreboardCommands.healthbar())
            .then(ModelDumpCommands.modeldump())
        );
    }
}
