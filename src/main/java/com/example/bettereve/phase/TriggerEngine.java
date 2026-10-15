package com.example.bettereve.phase;

import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.faction.Faction;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.faction.FactionRegistry;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.waypoint.WaypointManager;
import com.example.bettereve.waypoint.WaypointRegion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阶段触发器引擎 —— 所有"条件判定"都在这里，{@link PhaseManager} 只负责切阶段与打日志。
 *
 * 条件分三类（语义见 CONFIGURATION.md「triggers — 触发器」）：
 *
 * 1. 事件类（零轮询，事件到达即判）：{@code crystal_destroyed} / {@code entity_death} /
 *    {@code player_death}；命中后在本阶段内**锁定**（不因后续状态变化而复位）
 * 2. 轮询类（按各自 intervalTicks 采样）：{@code faction_wiped} / {@code entity_state} /
 *    {@code entity_health} / {@code zone_enter} / {@code zone_leave} / {@code score} /
 *    {@code command_check}；除 entity_health 外命中后同样锁定
 * 3. 组合类：{@code all_of} / {@code any_of}，子条件递归评估（子条件不写 targetPhase）
 *
 * 通用约定：
 * - 按数组顺序评估顶层触发器，**首个命中即切换并跳出**
 * - 进阶段重建全部状态（计数器清零、锁定复位、采样基线重记）
 * - 水晶不算；faction_wiped 数的是「在场」生物（含玩家与召唤物），
 *   口径见 countsAsFactionEntityPresent；水晶相关条件用 crystal_destroyed 或 entity_health + targetId
 * - 未识别的 type 静默忽略（老模组读新剧本不会炸）
 */
public final class TriggerEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerEngine.class);

    /** 轮询间隔默认值（tick） */
    private static final int DEFAULT_FACTION_INTERVAL = 5;
    private static final int DEFAULT_POLL_INTERVAL = 10;
    private static final int DEFAULT_COMMAND_INTERVAL = 20;

    /** 每个触发器的运行状态；进阶段整体清空（key = 剧本里的 TriggerDef 实例） */
    private static final Map<Scenario.TriggerDef, TriggerState> states = new HashMap<>();
    /** 已命中、等 delay 到点的切换（延迟期间冻结本阶段的条件评估与 timeout/timer） */
    private static PendingSwitch pending = null;

    /** 预定中的切换（触发器带 delay 时先记在这里，到点再切） */
    private static final class PendingSwitch {
        final String fromPhase;
        final Scenario.TriggerDef trigger;
        final String reason;
        final String winner;
        final int dueTick;

        PendingSwitch(String fromPhase, Scenario.TriggerDef trigger, String reason, String winner, int dueTick) {
            this.fromPhase = fromPhase;
            this.trigger = trigger;
            this.reason = reason;
            this.winner = winner;
            this.dueTick = dueTick;
        }
    }
    /** 服务端累计击杀数（按阵营）—— score 触发器用，剧本加载/记分板清零时复位 */
    private static final Map<String, Integer> killCounts = new HashMap<>();
    /** 服务端累计水晶摧毁数（按阵营） */
    private static final Map<String, Integer> crystalCounts = new HashMap<>();
    /** 配置/指令类问题只警告一次，避免刷屏 */
    private static final Set<String> warned = new HashSet<>();

    private TriggerEngine() {
    }

    /** 触发器运行状态（每触发器一份） */
    private static final class TriggerState {
        /** 本阶段内已命中（锁定），后续评估直接为真 */
        boolean latched;
        /** 轮询类：上次评估结果（未到期时复用） */
        boolean lastSatisfied;
        /** entity_death / player_death：本阶段累计命中次数 */
        int count;
        /** faction_wiped：本阶段召唤过（存活数 0 → 有）才武装 */
        boolean armed;
        /** faction_wiped：上次存活数（-1 = 还没采样） */
        int lastCount = -1;
        /** zone_*：上次区域内匹配实体数（-1 = 还没采样） */
        int lastInside = -1;
        /** 轮询类：下次到期 tick */
        int nextPollTick;
    }

    // ==================== 生命周期 ====================

    /** 进入新阶段：重建全部触发器状态（由 PhaseManager.switchToInternal 调用） */
    static void onPhaseEnter(Scenario.PhaseDef phase) {
        states.clear();
        pending = null;
    }

    /** 剧本加载 / 记分板清零：清空累计比分 */
    public static void resetScore() {
        killCounts.clear();
        crystalCounts.clear();
    }

    /** 清空指定阵营的累计比分（`/bettereve scoreboard clear <阵营>` 用） */
    public static void resetScoreFor(String factionId) {
        if (factionId == null) return;
        killCounts.remove(factionId);
        crystalCounts.remove(factionId);
    }

    // ==================== 事件入口 ====================

    /**
     * 实体死亡（PhaseManager 的 LivingDeathEvent 钩子调用）。
     * 只更新"死亡类"条件（entity_death / player_death，含组合条件里的子条件），随后立刻尝试切阶段。
     */
    public static void onEntityDeath(LivingEntity victim) {
        Scenario.PhaseDef phase = PhaseManager.getCurrentPhase();
        if (phase == null || phase.getTriggers() == null) return;
        for (Scenario.TriggerDef trigger : phase.getTriggers()) {
            updateDeathTriggers(trigger, victim);
        }
        tryFire(phase, PhaseManager.serverTicks());
    }

    /** 水晶被摧毁（CrystalSystem 调用）：记比分 + 更新 crystal_destroyed 条件 + 立刻尝试切阶段 */
    public static void onCrystalDestroyed(String attackerFaction, String crystalDefId) {
        if (attackerFaction != null && !attackerFaction.isEmpty()) {
            crystalCounts.merge(attackerFaction, 1, Integer::sum);
        }
        Scenario.PhaseDef phase = PhaseManager.getCurrentPhase();
        if (phase == null || phase.getTriggers() == null) return;
        for (Scenario.TriggerDef trigger : phase.getTriggers()) {
            markCrystalDestroyed(trigger, crystalDefId);
        }
        tryFire(phase, PhaseManager.serverTicks());
    }

    /** 击杀计分（CrystalSystem 调用；规则与记分板一致：召唤物不计分、同阵营不计分由调用方保证） */
    public static void onEntityKill(String attackerFaction) {
        if (attackerFaction == null || attackerFaction.isEmpty()) return;
        killCounts.merge(attackerFaction, 1, Integer::sum);
    }

    // ==================== 轮询 ====================

    /** 每 tick 由 PhaseManager 调用：先处理"待切换"，再评估本阶段条件 */
    static void tick(Scenario.PhaseDef phase, int serverTicks) {
        if (pending != null) {
            // 延迟期间阶段被别的途径切走（手动切换/重载）→ 丢弃预定
            if (pending.fromPhase == null || !pending.fromPhase.equals(PhaseManager.getCurrentPhaseId())) {
                pending = null;
                return;
            }
            if (serverTicks >= pending.dueTick) {
                PendingSwitch p = pending;
                pending = null;
                PhaseManager.fireTrigger(p.trigger, p.reason, p.winner);
            }
            return;   // 延迟期间冻结本阶段的条件评估
        }
        tryFire(phase, serverTicks);
    }

    /** 是否有"已命中、等 delay 到点"的切换（PhaseManager 用它冻结 timer / timeout） */
    static boolean isSwitchPending() {
        return pending != null;
    }

    // ==================== 评估与切换 ====================

    /** 按数组顺序评估顶层触发器，首个命中即切换（带 delay 则先预定）；返回是否命中 */
    private static boolean tryFire(Scenario.PhaseDef phase, int serverTicks) {
        if (pending != null) return false;   // 已有待切换：冻结评估，等它到点
        List<Scenario.TriggerDef> triggers = phase == null ? null : phase.getTriggers();
        if (triggers == null) return false;
        for (Scenario.TriggerDef trigger : triggers) {
            if (trigger.getTargetPhase() == null) continue;
            boolean satisfied = evaluate(trigger, serverTicks);
            if (!satisfied) continue;
            String reason = "faction_wiped".equals(trigger.getType()) ? "wiped" : trigger.getType();
            String winner = winnerFor(trigger, reason);
            int delaySeconds = Math.max(0, trigger.getDelay());
            if (delaySeconds > 0) {
                // 命中但先不切：留给播报 / 死亡动画 / 运镜收尾的时间
                pending = new PendingSwitch(PhaseManager.getCurrentPhaseId(), trigger, reason, winner,
                        serverTicks + delaySeconds * 20);
                LOGGER.info("[触发器] {} 命中，{} 秒后切换到 {}", trigger.getType(), delaySeconds, trigger.getTargetPhase());
            } else {
                PhaseManager.fireTrigger(trigger, reason, winner);
            }
            return true;
        }
        return false;
    }

    /** 日志胜者：触发器写了 winner 就用它；否则只有 wiped 能推断（唯一存活阵营），其余 none */
    private static String winnerFor(Scenario.TriggerDef trigger, String reason) {
        String override = trigger.getWinner();
        if (override != null && !override.isEmpty()) return override;
        return "wiped".equals(reason) ? survivingFaction() : "none";
    }

    /** 条件评估（含组合条件递归） */
    private static boolean evaluate(Scenario.TriggerDef trigger, int serverTicks) {
        String type = trigger.getType();
        if (type == null) return false;
        TriggerState st = state(trigger);
        switch (type) {
            // ---- 事件类：看锁定标记 ----
            case "crystal_destroyed":
            case "entity_death":
            case "player_death":
                return st.latched;

            // ---- 轮询类 ----
            case "faction_wiped":
                if (st.latched) return true;
                if (!due(st, trigger, serverTicks, DEFAULT_FACTION_INTERVAL)) return false;
                if (pollFactionWiped(trigger, st)) st.latched = true;
                return st.latched;
            case "entity_state":
                if (st.latched) return true;
                if (!due(st, trigger, serverTicks, DEFAULT_POLL_INTERVAL)) return false;
                if (pollEntityState(trigger)) st.latched = true;
                return st.latched;
            case "entity_health":
                if (!due(st, trigger, serverTicks, DEFAULT_POLL_INTERVAL)) return st.lastSatisfied;
                st.lastSatisfied = pollEntityHealth(trigger);
                return st.lastSatisfied;
            case "zone_enter":
            case "zone_leave":
                if (st.latched) return true;
                if (!due(st, trigger, serverTicks, DEFAULT_POLL_INTERVAL)) return false;
                if (pollZone(trigger, st)) st.latched = true;
                return st.latched;
            case "score":
                if (st.latched) return true;
                if (!due(st, trigger, serverTicks, DEFAULT_POLL_INTERVAL)) return false;
                if (pollScore(trigger)) st.latched = true;
                return st.latched;
            case "command_check":
                if (st.latched) return true;
                if (!due(st, trigger, serverTicks, DEFAULT_COMMAND_INTERVAL)) return false;
                if (pollCommand(trigger)) st.latched = true;
                return st.latched;

            // ---- 组合类 ----
            case "all_of": {
                List<Scenario.TriggerDef> children = trigger.getTriggers();
                if (children == null || children.isEmpty()) {
                    warnOnce("all_of-empty", "all_of 没写子条件，永不命中");
                    return false;
                }
                // 不短路：每个子条件都要持续采样（否则后面的子条件记不到基线，"本阶段召唤过才武装"永远不成立）
                boolean allSatisfied = true;
                for (Scenario.TriggerDef child : children) {
                    if (!evaluate(child, serverTicks)) allSatisfied = false;
                }
                return allSatisfied;
            }
            case "any_of": {
                List<Scenario.TriggerDef> children = trigger.getTriggers();
                if (children == null || children.isEmpty()) {
                    warnOnce("any_of-empty", "any_of 没写子条件，永不命中");
                    return false;
                }
                // 不短路：同上，保证每个子条件都被采样
                boolean anySatisfied = false;
                for (Scenario.TriggerDef child : children) {
                    if (evaluate(child, serverTicks)) anySatisfied = true;
                }
                return anySatisfied;
            }

            case "timer":
                // 由 PhaseManager 单独计时处理（阶段级单计数器）
                return false;

            default:
                // base_destroyed / custom 等未接线类型，以及拼错的类型：警告一次，永不命中
                warnOnce("unknown-type:" + type, "未识别的触发器类型：" + type + "（写了不生效）");
                return false;
        }
    }

    // ==================== 各条件的判定 ====================

    /**
     * faction_wiped：在场数从 >threshold 降到 ≤threshold 的那一刻命中（倒地/死亡动画中的生物仍算在场）。
     * 本阶段召唤过（在场数 0 → 有）才武装 —— 上一场清场后的空阵营不会立刻触发。
     */
    private static boolean pollFactionWiped(Scenario.TriggerDef trigger, TriggerState st) {
        String factionId = trigger.getFactionId();
        if (factionId == null || factionId.isEmpty()) {
            warnOnce("faction-wiped-no-faction", "faction_wiped 没写 factionId，永不命中");
            return false;
        }
        int threshold = trigger.getThreshold();
        int now = countFactionPresent(factionId);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[触发器] faction_wiped({}) 采样：在场={} 上次={} 武装={} 锁定={} 门槛={}",
                    factionId, now, st.lastCount, st.armed, st.latched, threshold);
        }
        if (st.lastCount < 0) {
            st.lastCount = now;   // 进阶段后的第一次采样：只记基线
            return false;
        }
        if (st.lastCount == 0 && now > 0) {
            st.armed = true;      // 本阶段召唤过 → 武装
        }
        boolean crossed = st.lastCount > threshold && now <= threshold && st.armed;
        st.lastCount = now;
        return crossed;
    }

    /** entity_state：本阶段内出现过"匹配实体 + NBT 探针命中"即命中（锁定） */
    private static boolean pollEntityState(Scenario.TriggerDef trigger) {
        Map<String, Object> probe = trigger.getNbt();
        if (probe == null || probe.isEmpty()) {
            warnOnce("entity-state-no-probe", "entity_state 没写 nbt 探针，永不命中");
            return false;
        }
        return anyEntity(e -> e instanceof LivingEntity living && living.isAlive()
                && !(e instanceof CrystalEntity)
                && TriggerMatcher.matches(e, trigger)
                && TriggerMatcher.matchesNbt(e, probe));
    }

    /**
     * entity_health：任一匹配实体（或 targetId 指定的水晶）血量低于门槛即命中。
     * ratio（0~1，按目标当前最大生命）优先，否则用 below 绝对值；当前状态判定，不锁定。
     */
    private static boolean pollEntityHealth(Scenario.TriggerDef trigger) {
        Double ratio = trigger.getRatio();
        Double below = trigger.getBelow();
        if (ratio == null && below == null) {
            warnOnce("entity-health-no-threshold", "entity_health 没写 ratio/below，永不命中");
            return false;
        }
        String crystalDefId = trigger.getTargetId();
        if (crystalDefId != null && !crystalDefId.isEmpty()) {
            return anyEntity(e -> e instanceof CrystalEntity crystal
                    && crystal.isAlive()
                    && crystalDefId.equals(crystal.getCrystalDefId())
                    && healthBelow(crystal, ratio, below));
        }
        return anyEntity(e -> e instanceof LivingEntity living && living.isAlive()
                && !(e instanceof CrystalEntity)
                && TriggerMatcher.matches(e, trigger)
                && healthBelow(living, ratio, below));
    }

    /** zone_enter / zone_leave：区域内匹配实体数 0→有 / 有→0 的那一次采样命中（锁定） */
    private static boolean pollZone(Scenario.TriggerDef trigger, TriggerState st) {
        WaypointRegion region = null;
        if (trigger.getWaypointId() != null && !trigger.getWaypointId().isEmpty()) {
            region = WaypointManager.getWaypointById(trigger.getWaypointId());
            if (region == null) {
                warnOnce("zone-bad-waypoint:" + trigger.getWaypointId(),
                        "zone 触发器引用的 waypointId 不存在：" + trigger.getWaypointId());
                return false;
            }
        } else if (trigger.getZone() == null) {
            warnOnce("zone-no-zone", "zone_enter / zone_leave 既没写 zone 也没写 waypointId，永不命中");
            return false;
        }
        final WaypointRegion r = region;
        final Scenario.KeepAliveZone z = trigger.getZone();
        int inside = countEntities(e -> e instanceof LivingEntity living && living.isAlive()
                && !(e instanceof CrystalEntity)
                && TriggerMatcher.matches(e, trigger)
                && (r != null ? r.isInside(e.getX(), e.getZ()) : z.contains(e.getX(), e.getZ())));
        if (LOGGER.isDebugEnabled()) {
            String bounds = r != null
                    ? String.format("%.0f,%.0f → %.0f,%.0f", r.getX1(), r.getZ1(), r.getX2(), r.getZ2())
                    : String.format("%.0f,%.0f → %.0f,%.0f", z.getX1(), z.getZ1(), z.getX2(), z.getZ2());
            LOGGER.debug("[触发器] {} 采样：区域内匹配实体={} 上次={} 区域=[{}]",
                    trigger.getType(), inside, st.lastInside, bounds);
        }
        if (st.lastInside < 0) {
            st.lastInside = inside;   // 第一次采样只记基线
            return false;
        }
        boolean hit = "zone_enter".equals(trigger.getType())
                ? (st.lastInside == 0 && inside > 0)
                : (st.lastInside > 0 && inside == 0);
        st.lastInside = inside;
        return hit;
    }

    /** score：服务端累计击杀/水晶数达到门槛即命中（计数在剧本加载或记分板清零时复位） */
    private static boolean pollScore(Scenario.TriggerDef trigger) {
        String factionId = trigger.getFactionId();
        if (factionId == null || factionId.isEmpty()) {
            warnOnce("score-no-faction", "score 没写 factionId，永不命中");
            return false;
        }
        String kind = trigger.getKind() == null || trigger.getKind().isEmpty() ? "kills" : trigger.getKind();
        int value;
        switch (kind) {
            case "kills" -> value = killCounts.getOrDefault(factionId, 0);
            case "crystals" -> value = crystalCounts.getOrDefault(factionId, 0);
            default -> {
                warnOnce("score-bad-kind:" + kind, "score 的 kind 只支持 kills / crystals，收到：" + kind);
                value = 0;
            }
        }
        return value >= Math.max(1, trigger.getThreshold());
    }

    /** command_check：跑一条只读指令，返回值 ≥1 即命中（默认每 20 tick 一次） */
    private static boolean pollCommand(Scenario.TriggerDef trigger) {
        String command = trigger.getCommand();
        if (command == null || command.isEmpty()) {
            warnOnce("command-check-empty", "command_check 没写 command，永不命中");
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        try {
            int result = server.getCommands().performPrefixedCommand(
                    CommandExecutor.commandSource(server), command);
            return result >= 1;
        } catch (Exception e) {
            warnOnce("command-check-fail:" + command,
                    "command_check 指令执行失败：" + command + " → " + e.getMessage());
            return false;
        }
    }

    // ==================== 死亡类条件的更新 ====================

    /** 递归更新"死亡类"条件（组合条件里的子条件也要更新） */
    private static void updateDeathTriggers(Scenario.TriggerDef trigger, LivingEntity victim) {
        String type = trigger.getType();
        if (type == null) return;
        if (trigger.getTriggers() != null) {
            for (Scenario.TriggerDef child : trigger.getTriggers()) {
                updateDeathTriggers(child, victim);
            }
            return;
        }
        if ("player_death".equals(type)) {
            if (victim instanceof Player && TriggerMatcher.matchesName(victim, trigger.getPlayer())) {
                bumpDeath(trigger);
            }
            return;
        }
        if ("entity_death".equals(type)
                && !(victim instanceof CrystalEntity)     // 水晶用 crystal_destroyed
                && TriggerMatcher.matches(victim, trigger)) {
            bumpDeath(trigger);
        }
    }

    /** 累计一次命中；达到门槛即锁定 */
    private static void bumpDeath(Scenario.TriggerDef trigger) {
        TriggerState st = state(trigger);
        st.count++;
        if (st.count >= requiredDeaths(trigger)) {
            st.latched = true;
        }
    }

    /** 死亡类条件的门槛：`count`，不写 = 1 */
    private static int requiredDeaths(Scenario.TriggerDef trigger) {
        return trigger.getCount() > 0 ? trigger.getCount() : 1;
    }

    /** 递归标记 crystal_destroyed 条件（组合条件里的子条件也要标记） */
    private static void markCrystalDestroyed(Scenario.TriggerDef trigger, String crystalDefId) {
        if ("crystal_destroyed".equals(trigger.getType())) {
            if (crystalDefId != null && crystalDefId.equals(trigger.getTargetId())) {
                state(trigger).latched = true;
            }
            return;
        }
        if (trigger.getTriggers() != null) {
            for (Scenario.TriggerDef child : trigger.getTriggers()) {
                markCrystalDestroyed(child, crystalDefId);
            }
        }
    }

    // ==================== 实体统计 ====================

    /** 阵营「在场」实体数 —— faction_wiped 的计数口径，见 countsAsFactionEntityPresent */
    private static int countFactionPresent(String factionId) {
        return countEntities(e -> countsAsFactionEntityPresent(e)
                && factionId.equals(FactionHelper.getEntityFactionId(e)));
    }

    /**
     * wipe 判定的「在场」口径：
     * - 玩家：按存活算（死在死亡界面上不算 —— 否则 wipe 会被一直挡住）；
     * - 其它生物：只要还没被移除就算在场。正在播死亡动画的尸体、倒地等复活的生物
     *   （如突变怪物的僵尸突变体：死亡后倒地 7 秒再站起来）都算在场，
     *   死亡流程真正走完（实体被移除）才从计数里消失 —— 这样"死了又活"不会误判 wipe。
     * - 水晶：不算。
     */
    private static boolean countsAsFactionEntityPresent(Entity entity) {
        if (entity instanceof CrystalEntity || !(entity instanceof LivingEntity living)) {
            return false;
        }
        return entity instanceof Player ? living.isAlive() : !entity.isRemoved();
    }

    /** 唯一在场阵营 id：恰好一个阵营还有在场实体才算胜者；双灭 / 多阵营并存 / 都没有 → none */
    private static String survivingFaction() {
        Map<String, Integer> counts = factionPresentCounts();
        String survivor = null;
        for (Faction faction : FactionRegistry.getAllFactions()) {
            if (counts.getOrDefault(faction.getId(), 0) > 0) {
                if (survivor != null) return "none";
                survivor = faction.getId();
            }
        }
        return survivor != null ? survivor : "none";
    }

    /** 一次遍历统计全部阵营的在场实体数（survivingFaction 用） */
    private static Map<String, Integer> factionPresentCounts() {
        Map<String, Integer> counts = new HashMap<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return counts;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (!countsAsFactionEntityPresent(entity)) continue;
                String factionId = FactionHelper.getEntityFactionId(entity);
                if (factionId.isEmpty()) continue;
                counts.merge(factionId, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** 血量是否低于门槛：ratio 优先（按目标当前最大生命），否则 below 绝对值 */
    private static boolean healthBelow(LivingEntity living, Double ratio, Double below) {
        double health = living.getHealth();
        if (ratio != null) return health <= living.getMaxHealth() * ratio;
        return health <= below;
    }

    /** 轮询节流：到点才重新采样（间隔取 intervalTicks，缺省用类型默认值） */
    private static boolean due(TriggerState st, Scenario.TriggerDef trigger, int serverTicks, int defaultInterval) {
        if (serverTicks < st.nextPollTick) return false;
        int interval = trigger.getIntervalTicks() != null
                ? Math.max(1, trigger.getIntervalTicks())
                : defaultInterval;
        st.nextPollTick = serverTicks + interval;
        return true;
    }

    private static TriggerState state(Scenario.TriggerDef trigger) {
        return states.computeIfAbsent(trigger, k -> new TriggerState());
    }

    // ==================== 实体遍历 ====================

    @FunctionalInterface
    private interface EntityFilter {
        boolean test(Entity entity);
    }

    /** 统计所有已加载实体（跨维度）里通过过滤的数量 */
    private static int countEntities(EntityFilter filter) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (filter.test(entity)) count++;
            }
        }
        return count;
    }

    /** 是否存在通过过滤的实体（命中即返回，不扫完） */
    private static boolean anyEntity(EntityFilter filter) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (filter.test(entity)) return true;
            }
        }
        return false;
    }

    /** 配置/指令类问题只警告一次，避免每 tick 刷屏 */
    private static void warnOnce(String key, String message) {
        if (warned.add(key)) {
            LOGGER.warn("[触发器] {}", message);
        }
    }
}
