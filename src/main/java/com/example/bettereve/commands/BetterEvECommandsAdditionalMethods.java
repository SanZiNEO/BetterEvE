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
    
    // 优化版本，使用新格式：/bettereve base create <阵营id> <x> <y> <z> <health> <攻击力> <护甲> <攻击范围> <攻击周期> <size>
    public static int executeCreateBaseOptimized(CommandContext<CommandSourceStack> context) {
        String factionId = StringArgumentType.getString(context, "factionId");
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        int health = IntegerArgumentType.getInteger(context, "health");
        int attackDamage = IntegerArgumentType.getInteger(context, "attackDamage");
        int armor = IntegerArgumentType.getInteger(context, "armor");
        double attackRange = DoubleArgumentType.getDouble(context, "attackRange");
        double attackCycle = DoubleArgumentType.getDouble(context, "attackCycle");
        String sizeKey = StringArgumentType.getString(context, "size");
        
        // 解析坐标
        BlockPos pos = new BlockPos(x, y, z);
        
        // 检查尺寸键值是否有效
        if (!BaseSystem.isValidSizeKey(sizeKey)) {
            context.getSource().sendFailure(Component.literal("无效的尺寸键值: " + sizeKey + 
                "，有效的尺寸格式为: 宽度x高度（例如 3x10）"));
            return 0;
        }
        
        // 调用 BaseSystem
        ServerLevel level = context.getSource().getLevel();
        double modelScale = 1.0; // 默认模型缩放
        
        BaseSystem.spawnBase(level, pos, factionId, health, attackDamage, armor, attackRange, attackCycle, sizeKey, modelScale);
        
        context.getSource().sendSuccess(() -> Component.literal("基地创建成功: " + 
            FactionRegistry.getFaction(factionId).getName() + 
            " 位置: " + pos.toShortString() + 
            " 尺寸: " + sizeKey), true);
        return 1;
    }
}
