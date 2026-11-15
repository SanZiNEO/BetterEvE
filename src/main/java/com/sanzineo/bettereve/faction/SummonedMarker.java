package com.sanzineo.bettereve.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * 召唤物 NBT 标记。
 *
 * 写入方式与 {@link FactionHelper} 一致，使用 getPersistentData()，
 * 数据随存档持久化，重启后仍可读取。
 *
 * 标记体系：
 * - {@code BetterEvE:Summoned}          召唤物布尔标记（其他系统的一等公民）
 * - {@code BetterEvE:SummonerId}        召唤者 UUID（追溯用）
 * - {@code BetterEvE:SummonedReason}    判定来源，便于排查误判
 * - {@code BetterEvE:SummonedAt}        标记时的 gameTime（调试用）
 *
 * 判定来源取值：OWNER / OWNER_UUID / TARGET / CONTEXT / MANUAL
 * （TARGET 为历史值：目标反推规则已删除，只在旧存档里还会读到）
 */
public final class SummonedMarker {

    public static final String SUMMONED_KEY = "BetterEvE:Summoned";
    public static final String SUMMONER_ID_KEY = "BetterEvE:SummonerId";
    public static final String SUMMONED_REASON_KEY = "BetterEvE:SummonedReason";
    public static final String SUMMONED_AT_KEY = "BetterEvE:SummonedAt";

    // ==================== 判定来源常量 ====================
    /** 由 TraceableEntity.getOwner() 判定 */
    public static final String REASON_OWNER = "OWNER";
    /** 由 OwnableEntity（getOwner / getOwnerUUID 反查）判定 */
    public static final String REASON_OWNER_UUID = "OWNER_UUID";
    /** 由 NBT 召唤者痕迹判定（跨模组主通道，如咒术回战的 OWNER_UUID 键） */
    public static final String REASON_OWNER_NBT = "OWNER_NBT";
    /** 由"目标即召唤者"反推判定（**历史值，已不再产生**：该规则已删除，见 SummonedFactionInheritanceHandler 的说明） */
    public static final String REASON_TARGET = "TARGET";
    /** 由召唤者上下文（生成动作捕获）判定 */
    public static final String REASON_CONTEXT = "CONTEXT";
    /** 由指令/外部主动标记 */
    public static final String REASON_MANUAL = "MANUAL";
    /** 多部件生物的部件：由 ParentUUID 链接到主体，属于结构件而非独立召唤物 */
    public static final String REASON_PARENT = "PARENT";

    /** 多部件生物的主体标记（自身不是部件，但有部件指向它） */
    public static final String MULTIPART_ROOT_KEY = "BetterEvE:MultipartRoot";

    // ==================== 多部件生物链接键 ====================

    /**
     * 多部件生物（长条型 boss 的身体节）用来指向"上一节/主体"的 NBT 键。
     *
     * 实测来源：
     * - Alex's Mobs EntityVoidWormPart / EntityAnacondaPart / EntityCentipedeBody
     *   存档写入 putUUID("ParentUUID", getParentId())，逐节链式上溯（尾→身→头）
     * - 其他模组可能用小写或带下划线的变体
     */
    private static final String[] PARENT_LINK_KEYS = {
            "ParentUUID", "parentUUID", "PARENT_UUID", "ParentUuid", "parent_uuid"
    };

    /** 标记为多部件生物的主体 */
    public static void markMultipartRoot(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentData().putBoolean(MULTIPART_ROOT_KEY, true);
    }

    /** 该实体是否是多部件生物的主体（有其它部件挂在它身上） */
    public static boolean isMultipartRoot(Entity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.contains(MULTIPART_ROOT_KEY) && nbt.getBoolean(MULTIPART_ROOT_KEY);
    }

    /**
     * 判断该实体是否是"多部件生物的部件"。
     * 部件有 ParentUUID 链接，但没有召唤者语义 —— 属于主体的一部分，不是召唤物。
     */
    public static boolean isMultipartPiece(Entity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag nbt = entity.getPersistentData();
        for (String key : PARENT_LINK_KEYS) {
            if (nbt.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取多部件生物的父实体 UUID（用于逐节上溯到主体）。
     *
     * @return 父实体 UUID，无则 null
     */
    public static UUID getParentLinkUuid(Entity entity) {
        if (entity == null) {
            return null;
        }
        CompoundTag nbt = entity.getPersistentData();
        for (String key : PARENT_LINK_KEYS) {
            if (nbt.contains(key)) {
                UUID found = readUuidFromTag(nbt, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private SummonedMarker() {
    }

    /** 该实体是否被标记为召唤物 */
    public static boolean isSummoned(Entity entity) {
        if (entity == null) {
            return false;
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.contains(SUMMONED_KEY) && nbt.getBoolean(SUMMONED_KEY);
    }

    /**
     * 标记为召唤物。
     *
     * @param entity      被标记的实体
     * @param reason      判定来源，见上方 REASON_* 常量
     * @param summonerUuid 召唤者 UUID，可为 null
     */
    public static void markSummoned(Entity entity, String reason, UUID summonerUuid) {
        if (entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean(SUMMONED_KEY, true);
        if (reason != null && !reason.isEmpty()) {
            nbt.putString(SUMMONED_REASON_KEY, reason);
        }
        if (summonerUuid != null) {
            nbt.putString(SUMMONER_ID_KEY, summonerUuid.toString());
        }
        if (!entity.level().isClientSide()) {
            nbt.putLong(SUMMONED_AT_KEY, entity.level().getGameTime());
        }
    }

    /** 读取召唤者 UUID，无则返回 null */
    public static UUID getSummonerUuid(Entity entity) {
        if (entity == null) {
            return null;
        }
        CompoundTag nbt = entity.getPersistentData();
        if (!nbt.contains(SUMMONER_ID_KEY)) {
            return null;
        }
        String raw = nbt.getString(SUMMONER_ID_KEY);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            // NBT 被外部写坏：抛出去比静默吞掉更容易定位
            throw new IllegalStateException(
                    "实体 " + entity.getType() + " 的 " + SUMMONER_ID_KEY + " 不是合法 UUID: " + raw, e);
        }
    }

    /** 读取判定来源，无则返回空串 */
    public static String getSummonedReason(Entity entity) {
        if (entity == null) {
            return "";
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.contains(SUMMONED_REASON_KEY) ? nbt.getString(SUMMONED_REASON_KEY) : "";
    }

    /** 读取标记时的 gameTime，无则返回 -1 */
    public static long getSummonedAt(Entity entity) {
        if (entity == null) {
            return -1L;
        }
        CompoundTag nbt = entity.getPersistentData();
        return nbt.contains(SUMMONED_AT_KEY) ? nbt.getLong(SUMMONED_AT_KEY) : -1L;
    }

    /** 清除召唤物标记（解除归属时使用） */
    public static void clearSummoned(Entity entity) {
        if (entity == null) {
            return;
        }
        CompoundTag nbt = entity.getPersistentData();
        nbt.remove(SUMMONED_KEY);
        nbt.remove(SUMMONER_ID_KEY);
        nbt.remove(SUMMONED_REASON_KEY);
        nbt.remove(SUMMONED_AT_KEY);
    }

    // ==================== 跨模组召唤者痕迹解析 ====================

    /**
     * 痕迹键名匹配词。模组普遍用这些词命名"召唤者"相关的 NBT 键。
     *
     * 实测来源（扫描整个整合包 48 个模组得出）：
     * - OWNER_UUID / OwnerUUID / ownerUUID / owner_entity / OWNER_UNIQUE_ID / OWNER_ENTITY_ID
     *   （咒术回战、百年战争、冰火传说、新生魔艺、MutantMonsters、灾厄侵扰、神圣RPG、虚无世界）
     * - SUMMONER_ID / SummonerUUID / summoner / summon_owner / summon_hostile
     *   （冰火传说、诡厄巫法）
     * - casterUuid / CASTER / cachedCaster / casterTag
     *   （传奇怪物、灾变、Mowzie's、蔚蓝浩空、新生魔艺）
     * - CREATOR_ID / setCreatorEntityUUID（灾变）
     * - CONTROLLER_UUID / ControllerUUID（灾变）
     * - host（冰火传说）
     * - parent / ParentUUID / PARENT_UUID（Alex's Mobs、冰火传说）
     * - TAG_OWNER / TAG_OWNER_NAME（诡厄巫法）
     */
    private static final String[] OWNER_KEY_HINTS = {
            "owner", "summon", "master", "belong", "caster", "creator", "host", "parent"
    };

    /**
     * 显式优先键：命中即用，省去遍历全部键。全部来自实测。
     */
    private static final String[] PRIORITY_KEYS = {
            "OWNER_UUID", "OwnerUUID", "ownerUUID", "ownerUuid",
            "OWNER_UNIQUE_ID", "OWNER_ENTITY_ID",
            "SUMMONER_ID", "SummonerUUID", "summonerUUID",
            "CASTER", "casterUuid", "casterUUID",
            "CREATOR_ID", "CONTROLLER_UUID",
            "parentUUID", "ParentUUID", "PARENT_UUID",
            "owner_entity", "ownerID", "ownerId"
    };

    public static boolean isOwnerLikeKey(String key) {
        String lower = key.toLowerCase();
        for (String hint : OWNER_KEY_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从实体的 NBT 里解析召唤者 UUID —— 跨模组痕迹读取，不做任何反射。
     *
     * 实测存法有两种，都要支持：
     * 1. getUUID / putUUID（UUID 类型）—— 百年战争、冰火传说、灾变等
     * 2. putString(getStringUUID())（字符串型 UUID）—— 咒术回战 OWNER_UUID 属于这种
     *
     * @return 召唤者 UUID，找不到返回 null
     */
    public static UUID resolveSummonerUuid(Entity entity) {
        if (entity == null) {
            return null;
        }
        CompoundTag nbt = entity.getPersistentData();

        // 第一轮：显式优先键（顺序稳定、命中率最高）
        for (String key : PRIORITY_KEYS) {
            if (nbt.contains(key)) {
                UUID found = readUuidFromTag(nbt, key);
                if (found != null) {
                    return found;
                }
            }
        }

        // 第二轮：按关键词遍历全部键（覆盖未列入优先表的所有模组）
        for (String key : nbt.getAllKeys()) {
            if (key.startsWith("BetterEvE:")) {
                continue; // 跳过自己的标记，避免自引用
            }
            if (!isOwnerLikeKey(key)) {
                continue;
            }
            UUID found = readUuidFromTag(nbt, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 从单个 NBT 键读取 UUID，支持实测的两种存法。
     * 读不出来返回 null（键里可能是实体名、计数值等非 UUID 内容）。
     */
    private static UUID readUuidFromTag(CompoundTag nbt, String key) {
        net.minecraft.nbt.Tag tag = nbt.get(key);
        if (tag == null) {
            return null;
        }

        // 存法 1：UUID 类型（原版 putUUID 会写成 IntArrayTag(4) 或 LongArrayTag）
        if (tag instanceof net.minecraft.nbt.IntArrayTag || tag instanceof net.minecraft.nbt.LongArrayTag) {
            try {
                return nbt.getUUID(key);
            } catch (Exception e) {
                return null; // 数组长度不是 4，不是 UUID
            }
        }

        // 存法 2：字符串型（咒术回战用 getStringUUID() 写入）
        if (tag instanceof net.minecraft.nbt.StringTag) {
            return parseUuidLenient(nbt.getString(key));
        }

        return null;
    }

    private static UUID parseUuidLenient(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null; // 不是 UUID（可能是实体名、标记字符串），由调用方继续尝试其它键
        }
    }
}
