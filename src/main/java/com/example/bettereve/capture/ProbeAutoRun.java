package com.example.bettereve.capture;

import com.example.bettereve.Bettereve;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 探针测试钩子（默认惰性）：设了环境变量 {@code BETTEREVE_PROBE=id1,id2} 时，
 * 世界加载 40 tick 后自动通过聊天栏发送<b>正式指令</b>，用于无人值守跑探针。
 * 不设这个变量时它什么都不做。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ProbeAutoRun {

    private static int ticks = 0;
    private static boolean done = false;

    private ProbeAutoRun() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        String spec = System.getenv("BETTEREVE_PROBE");
        if (spec == null || spec.isBlank()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ticks = 0;
            return;
        }
        ticks++;
        if (ticks < 40) {
            return;
        }
        done = true;
        for (String target : spec.split(",")) {
            String id = target.trim();
            if (id.isEmpty()) {
                continue;
            }
            try {
                mc.player.connection.sendCommand("bettereve modeldump probe " + id);
            } catch (Throwable t) {
                System.out.println("[modeldump-probe] sendCommand failed for " + id + ": " + t);
            }
        }
    }
}
