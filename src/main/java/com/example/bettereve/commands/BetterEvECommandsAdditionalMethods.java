package com.example.bettereve.commands;

import com.example.bettereve.base.BaseSystem;
import com.example.bettereve.faction.FactionRegistry;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class BetterEvECommandsAdditionalMethods {
    public static int executeCreateBaseWithAllAttributes(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "factionId");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        int health = IntegerArgumentType.getInteger(context, "health");
        int attackDamage = IntegerArgumentType.getInteger(context, "attackDamage");
        int armor = IntegerArgumentType.getInteger(context, "armor");
        double attackRange = DoubleArgumentType.getDouble(context, "attackRange");
        double attackCycle = DoubleArgumentType.getDouble(context, "attackCycle");
        double collisionSize = DoubleArgumentType.getDouble(context, "collisionSize");
        double collisionHeight = DoubleArgumentType.getDouble(context, "collisionHeight");
        BlockPos pos = new BlockPos(x, y, z);
        ServerLevel level = context.getSource().getLevel();
        
        BaseSystem.spawnBase(level, pos, factionId, health, attackDamage, armor, attackRange, attackCycle, collisionSize, collisionHeight);
        context.getSource().sendSuccess(() -> Component.literal("基地创建成功: " + FactionRegistry.getFaction(factionId).getName()), true);
        return 1;
    }
}
