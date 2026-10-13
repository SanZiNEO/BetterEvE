package com.example.bettereve.capture;

import com.example.bettereve.Bettereve;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 客户端侧：每 tick 取 {@link ProbeRequests} 的待办，在 Render thread 上跑探针。
 * <p>
 * 挂在 {@code ClientTickEvent} 的 {@link TickEvent.Phase#END}（计划 §02 §5）。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ModelDumpClientTick {

    private ModelDumpClientTick() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ResourceLocation id;
        while ((id = ProbeRequests.poll()) != null) {
            String result;
            try {
                result = ProbeRunner.probe(id);
            } catch (Throwable t) {
                result = "EXCEPTION: " + t;
            }
            String line = id + " -> " + result;
            System.out.println("[modeldump-probe] " + line);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[modeldump] " + line), false);
            }
            appendLog(line);
        }
    }

    private static void appendLog(String line) {
        try {
            Path out = FMLPaths.GAMEDIR.get().resolve("bettereve-dump").resolve("probe").resolve("_probe_run.txt");
            Files.createDirectories(out.getParent());
            Files.writeString(out, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }
}
