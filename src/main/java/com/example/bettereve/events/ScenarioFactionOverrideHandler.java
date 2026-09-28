package com.example.bettereve.events;

import com.example.bettereve.Bettereve;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.faction.SummonRule;
import com.example.bettereve.network.FactionSyncHandler;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * 剧本强制阵营 —— 按实体类型点名钉死阵营，优先级高于一切继承逻辑。
 *
 * 设计动机：有些实体的归属在模组内部就被清空（实测咒术回战的茈，
 * HollowPurpleProcedure 的 cnt2==2.0 分支把 OWNER_UUID 写成空串，同时置
 * explode + betrayal），继承链拿不到任何证据，于是它变成中立生物见谁打谁，
 * 实测会打到五条。这类实体只能由剧本直接指定阵营。
 *
 * 为什么必须是 HIGHEST：{@link SummonedFactionInheritanceHandler} 在 NORMAL
 * 优先级上跑，且以"已有非空阵营 → 绝不覆盖"为第一规则。本 handler 先跑并写入
 * 阵营，继承逻辑随后自然跳过该实体（不继承、不改写）。
 *
 * 只写阵营，不写等级：等级由剧本的 levelTableOverrides 在 EntityLevelSystem
 * 赋等级时决定，与阵营来源无关。
 */
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScenarioFactionOverrideHandler {
    private static final Logger LOGGER = LogManager.getLogger("BetterEvE/FactionOverride");

    /** 已警告过的无效阵营 id，避免每只实体刷一行 */
    private static final Set<String> WARNED_UNKNOWN_FACTIONS = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 与继承 handler 同款边界：客户端、存档加载的实体都不处理
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();

        String factionId = SummonRule.factionOverrideFor(entity);
        if (factionId == null) {
            return;
        }

        // 剧本写错阵营名时不留半个状态：警告一次后放行，实体回到原有继承流程
        if (FactionRegistry.getFaction(factionId) == null) {
            if (WARNED_UNKNOWN_FACTIONS.add(factionId)) {
                LOGGER.warn("[阵营覆盖] 剧本 factionOverrides 指定的阵营不存在：{}（首个命中实体 {}），已忽略",
                        factionId, entity.getType());
            }
            return;
        }

        FactionHelper.setEntityFactionId(entity, factionId);
        FactionSyncHandler.syncEntityImmediately(entity);
        LOGGER.info("[阵营覆盖] {} → {} (剧本 factionOverrides)", entity.getType(), factionId);
    }
}
