package com.sanzineo.bettereve.commands;

import com.sanzineo.bettereve.capture.ProbeRequests;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import static com.sanzineo.bettereve.commands.CommandSuggestions.ENTITY_TYPE_SUGGESTIONS;

/** `/bettereve modeldump ...` 子树：请求客户端模型探针。 */
public final class ModelDumpCommands {

    private ModelDumpCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> modeldump() {
        return Commands.literal("modeldump")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("probe")
                .then(Commands.argument("entity", ResourceLocationArgument.id())
                    .suggests(ENTITY_TYPE_SUGGESTIONS)
                    .executes(ModelDumpCommands::executeModelDumpProbe)
                )
            );
    }

    /** `/bettereve modeldump probe <entity>` —— 把请求排进队列，由客户端 tick 执行。 */
    private static int executeModelDumpProbe(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "entity");
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            context.getSource().sendFailure(Component.literal("未知实体类型: " + id));
            return 0;
        }
        ProbeRequests.request(id);
        context.getSource().sendSuccess(
                () -> Component.literal("已请求探针: " + id + "（结果见客户端聊天栏 / bettereve-dump/probe/）"), false);
        return 1;
    }
}
