package com.example.bettereve.commands;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/** 命令执行器之间共享的参数读取工具。 */
public final class CommandArgs {

    private CommandArgs() {
    }

    /** 读取可选参数：该参数不在本次解析路径里时 Brigadier 抛 IllegalArgumentException，这里返回 null */
    public static <T> T optArg(CommandContext<CommandSourceStack> context, String name, Class<T> type) {
        try {
            return context.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
