package com.example.bettereve.phase;

import com.example.bettereve.faction.FactionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 触发器目标匹配 —— 实体类型 / 自定义名 / NBT 探针 / 阵营 四种过滤，全项目共用一套。
 *
 * 与 {@code options.rules} 的匹配语义一致（{@link com.example.bettereve.scenario.ScenarioRules}
 * 与 {@code ScenarioRuleHandler} 都复用本类），避免"剧本里两处写法一样但行为不同"。
 *
 * 约定：
 * - 类型支持 {@code modid:name} 与只写实体名（如 {@code zombie}）两种写法
 * - 名字比较前会去掉 {@code §} 颜色代码
 * - NBT 探针：布尔比对布尔键，其余比对字符串键（探针取自实体序列化 NBT）
 * - 每个过滤项"不写 = 不过滤"；探针/名字写空串按不写处理
 */
public final class TriggerMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerMatcher.class);

    private TriggerMatcher() {
    }

    /** 实体类型匹配：type 为空 = 不过滤 */
    public static boolean matchesType(Entity entity, String type) {
        if (type == null || type.isEmpty()) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return false;
        }
        return type.equals(key.toString()) || type.equals(key.getPath());
    }

    /** 自定义名匹配（去颜色代码后逐字比较）：name 为空 = 不过滤 */
    public static boolean matchesName(Entity entity, String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        String custom = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
        return name.equals(ChatFormatting.stripFormatting(custom));
    }

    /** 阵营匹配：factionId 为空 = 不过滤 */
    public static boolean matchesFaction(Entity entity, String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            return true;
        }
        return factionId.equals(FactionHelper.getEntityFactionId(entity));
    }

    /**
     * NBT 探针：把实体序列化成 NBT，逐项比对规则里写的键值。
     * 空探针 = 不过滤（调用方若要求"必须写探针"，请自行判空）。
     */
    public static boolean matchesNbt(Entity entity, Map<String, Object> probe) {
        if (probe == null || probe.isEmpty()) {
            return true;
        }
        try {
            CompoundTag tag = entity.saveWithoutId(new CompoundTag());
            for (Map.Entry<String, Object> entry : probe.entrySet()) {
                Object expected = entry.getValue();
                if (expected instanceof Boolean bool) {
                    if (tag.getBoolean(entry.getKey()) != bool) {
                        return false;
                    }
                } else if (!String.valueOf(expected).equals(tag.getString(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            // 轮询/事件路径里不能把异常抛进 tick：记一次上下文后跳过这只实体
            LOGGER.error("[触发器匹配] 读取实体 NBT 失败 entity={} type={}", entity.getUUID(), entity.getType(), t);
            return false;
        }
    }

    /** 组合匹配：四种过滤同时满足才算命中 */
    public static boolean matches(Entity entity, String entityType, String name,
                                  Map<String, Object> nbt, String factionId) {
        return matchesType(entity, entityType)
                && matchesName(entity, name)
                && matchesFaction(entity, factionId)
                && matchesNbt(entity, nbt);
    }

    /** 组合匹配（直接取触发器字段） */
    public static boolean matches(Entity entity, com.example.bettereve.scenario.Scenario.TriggerDef trigger) {
        return matches(entity, trigger.getEntityType(), trigger.getName(),
                trigger.getNbt(), trigger.getFactionId());
    }
}
