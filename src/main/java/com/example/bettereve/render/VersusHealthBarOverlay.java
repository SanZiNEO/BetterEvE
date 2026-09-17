package com.example.bettereve.render;

import com.example.bettereve.Bettereve;
import com.example.bettereve.faction.ClientFactionCache;
import com.example.bettereve.faction.FactionHelper;
import com.example.bettereve.scenario.Scenario;
import com.example.bettereve.scenario.ScenarioManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 格斗血条 HUD —— 顶部左右两侧的阵营血条组（格斗游戏风格）。
 * <p>
 * 与实体头顶血条（{@link SimpleHealthBarRenderer}）、计分板（{@link ScoreboardOverlay}）
 * 完全独立，由剧本 {@code options.versusHealthBar} 单独开关。
 * <p>
 * 布局规则：
 * <ul>
 *   <li>阵营数为偶数 → 左右各一半；为奇数 → 正中间 1 个 + 左右各一半</li>
 *   <li>每个阵营一个血条组：主血条在上，次要血条向下依次排布，每条为上方血条的 2/5 长</li>
 *   <li>左侧血条组左对齐（从右往左减少），右侧完全镜像</li>
 * </ul>
 * <p>
 * 排序规则：
 * <ul>
 *   <li>主血条 = 该阵营血量上限最高的生物，绑定后不换，直到该生物死亡</li>
 *   <li>次要血条 = 其余生物按血量上限顺位，每次扫描刷新</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Bettereve.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class VersusHealthBarOverlay {

    // ==================== 布局常量 ====================
    /** 血条组距屏幕顶部 */
    private static final int TOP_MARGIN = 6;
    /** 血条组之间的水平间距 */
    private static final int GROUP_GAP = 24;
    /** 屏幕左右边缘留白 */
    private static final int SIDE_MARGIN = 10;
    /** 血条之间的垂直间距 */
    private static final int BAR_V_GAP = 2;
    /** 名字与血条组之间的间距 */
    private static final int NAME_GAP = 3;
    /** 次要血条相对上方血条的视觉长度比例 */
    private static final float SUB_LENGTH_RATIO = 0.4f;
    /** 次要血条的整体缩放（视觉尺寸小于主血条） */
    private static final float SUB_SCALE = 0.6f;
    /** 次要血条名字的字号比例 */
    private static final float SUB_NAME_SCALE = 0.75f;
    /** 名字颜色 */
    private static final int NAME_COLOR = 0xFFFFFFFF;

    // ==================== 贴图常量 ====================
    // 尺寸必须与 tools/generate_versus_textures.py 保持一致
    /** 端头贴图边长（纯圆，血条从圆心处铺入，圆自然盖住血条左端） */
    private static final int TEX_HEAD_SIZE = 68;
    /** 圆心 X = 血条起点偏移 */
    private static final int TEX_HEAD_CX = 34;
    /** 圆半径 */
    private static final int TEX_HEAD_R = 30;
    /** 圆与血条的交界处（次血条左缘对齐到这里） */
    private static final int TEX_HEAD_JUNCTION = TEX_HEAD_CX + TEX_HEAD_R;
    /** 血条高度 */
    private static final int TEX_BAR_H = 18;
    /** 中间条/填充层贴图宽度（水平平铺单元） */
    private static final int TEX_TILE_W = 64;

    private static final ResourceLocation TEX_MID =
            new ResourceLocation(Bettereve.MODID, "textures/ui/versus/versus_mid.png");
    private static final ResourceLocation TEX_HEAD_OUT =
            new ResourceLocation(Bettereve.MODID, "textures/ui/versus/versus_head_out.png");

    /** 填充层贴图：按剧本 versusHealthBarStyle 选择 */
    private static ResourceLocation fillTexture(String style) {
        String s = (style == null || style.isEmpty()) ? "plain" : style;
        return new ResourceLocation(Bettereve.MODID, "textures/ui/versus/versus_fill_" + s + ".png");
    }

    // ==================== 扫描常量 ====================
    /** 扫描间隔（tick）：1 秒 */
    private static final int SCAN_INTERVAL_TICKS = 20;

    // ==================== 运行时状态 ====================
    /** 每个阵营当前绑定的主血条实体 ID（-1 = 未绑定） */
    private static final Map<String, Integer> mainBarBinding = new HashMap<>();
    /** 上次扫描的 tick */
    private static long lastScanTick = -1;
    /** 扫描结果：阵营 ID → 该阵营的生物列表（已按血量上限降序） */
    private static final Map<String, List<LivingEntity>> factionMembers = new LinkedHashMap<>();

    /**
     * 死亡通知入口，由 {@code VersusBarDeathPacket} 调用。
     * 若死亡的是某个阵营当前绑定的主血条生物，立即解绑，下次渲染时重排。
     */
    public static void onEntityDied(int victimEntityId) {
        mainBarBinding.entrySet().removeIf(e -> e.getValue() == victimEntityId);
    }

    // ==================== 覆盖层注册 ====================

    public static final IGuiOverlay VERSUS_HEALTH_BAR_OVERLAY =
            (gui, guiGraphics, partialTick, width, height) -> render(guiGraphics, width);

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "bettereve_versus_health_bar",
                VERSUS_HEALTH_BAR_OVERLAY);
    }

    // ==================== 渲染 ====================

    private static void render(GuiGraphics graphics, int screenWidth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.font == null) return;

        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getOptions() == null) return;
        Scenario.ScenarioOptions options = scenario.getOptions();
        if (!options.isVersusHealthBar()) return;

        List<Scenario.FactionDef> factionDefs = scenario.getFactions();
        if (factionDefs == null || factionDefs.isEmpty()) return;

        scanIfDue(mc, factionDefs);

        // 只保留场上有生物的阵营
        List<Scenario.FactionDef> active = new ArrayList<>();
        for (Scenario.FactionDef def : factionDefs) {
            List<LivingEntity> members = factionMembers.get(def.getId());
            if (members != null && !members.isEmpty()) {
                active.add(def);
            }
        }
        if (active.isEmpty()) return;

        int subCount = Math.max(0, options.getVersusHealthBarSubCount());
        String style = options.getVersusHealthBarStyle();
        int count = active.size();
        int groupWidth = computeGroupWidth(screenWidth, count);

        // 偶数：左右各半；奇数：中间 1 个 + 左右各半
        int centerIndex = (count % 2 == 1) ? count / 2 : -1;
        int leftCount = count / 2;
        int rightCount = count - leftCount - (centerIndex >= 0 ? 1 : 0);

        // 左侧阵营：从最左向右排
        for (int i = 0; i < leftCount; i++) {
            int x = SIDE_MARGIN + i * (groupWidth + GROUP_GAP);
            renderGroup(graphics, mc.font, active.get(i), x, groupWidth, subCount, false, style);
        }
        // 中间阵营：水平居中
        if (centerIndex >= 0) {
            int x = (screenWidth - groupWidth) / 2;
            renderGroup(graphics, mc.font, active.get(centerIndex), x, groupWidth, subCount, false, style);
        }
        // 右侧阵营：镜像，从最右向左排
        for (int i = 0; i < rightCount; i++) {
            int idx = leftCount + (centerIndex >= 0 ? 1 : 0) + i;
            int x = screenWidth - SIDE_MARGIN - groupWidth - (rightCount - 1 - i) * (groupWidth + GROUP_GAP);
            renderGroup(graphics, mc.font, active.get(idx), x, groupWidth, subCount, true, style);
        }
    }

    /**
     * 计算单个血条组的宽度：屏幕可用宽度按阵营数等分，扣除组间距。
     */
    private static int computeGroupWidth(int screenWidth, int factionCount) {
        int usable = screenWidth - SIDE_MARGIN * 2;
        int gaps = Math.max(0, factionCount - 1) * GROUP_GAP;
        int width = (usable - gaps) / Math.max(1, factionCount);
        return Math.max(40, width);
    }

    /**
     * 渲染一个阵营的血条组。
     *
     * @param mirrored 右侧阵营为 true：整体水平翻转（端头仍在屏幕外侧）
     */
    private static void renderGroup(GuiGraphics graphics, Font font, Scenario.FactionDef def,
                                    int x, int groupWidth, int subCount, boolean mirrored, String style) {
        List<LivingEntity> members = factionMembers.get(def.getId());
        if (members == null || members.isEmpty()) return;

        int color = 0xFF000000 | def.getColorInt();

        // 横条可见区：端头圆占据外侧 TEX_HEAD_JUNCTION 宽度，横条从这里开始露出。
        // 名字与次血条都以这个可见区为基准对齐（不是血条组左缘，那样会压到圆上方）。
        int visibleW = groupWidth - TEX_HEAD_JUNCTION;
        if (visibleW <= 0) return;

        int y = TOP_MARGIN;

        // ==================== 行 1：主血条 ====================
        // 端头比血条高，横条在行内垂直居中；名字紧贴横条上缘（不是行顶，否则会飘到端头上方）
        int barTopInRow = (TEX_HEAD_SIZE - TEX_BAR_H) / 2;
        LivingEntity main = members.get(0);
        drawName(graphics, font, main.getName(),
                x, y + barTopInRow - NAME_GAP - font.lineHeight,
                groupWidth, mirrored, 1.0f, TEX_HEAD_JUNCTION);

        drawBar(graphics, x, y, groupWidth, healthPercent(main), color, style, mirrored, true);
        // 推进到主血条下缘（端头圆在水平方向只延伸到交界处，纵向不与次血条重叠）
        y += barTopInRow + TEX_BAR_H + BAR_V_GAP;

        // ==================== 行 2..n：次要血条（逐级 2/5 长度） ====================
        // 左缘（镜像时为右缘）锚定在交界处，向内延伸
        int subAnchor = mirrored ? x + groupWidth - TEX_HEAD_JUNCTION : x + TEX_HEAD_JUNCTION;
        float visualLength = visibleW;
        int shown = Math.min(subCount, members.size() - 1);
        for (int i = 0; i < shown; i++) {
            visualLength *= SUB_LENGTH_RATIO;
            int logicalWidth = Math.max(24, Math.round(visualLength / SUB_SCALE));
            int barWidth = Math.round(logicalWidth * SUB_SCALE);
            LivingEntity sub = members.get(i + 1);

            // 条身位置：镜像时以锚点为右缘
            int barX = mirrored ? subAnchor - barWidth : subAnchor;

            // 名字：小一号，对齐条身自身
            drawName(graphics, font, sub.getName(), barX, y, barWidth, mirrored, SUB_NAME_SCALE, 0);
            y += Math.round(font.lineHeight * SUB_NAME_SCALE) + NAME_GAP;

            graphics.pose().pushPose();
            graphics.pose().translate(barX, y, 0);
            graphics.pose().scale(SUB_SCALE, SUB_SCALE, 1.0f);   // 正缩放，不翻转绕序
            drawBar(graphics, 0, 0, logicalWidth, healthPercent(sub), color, style, mirrored, false);
            graphics.pose().popPose();

            y += Math.round(TEX_BAR_H * SUB_SCALE) + BAR_V_GAP;
        }
    }

    /**
     * 绘制名字。
     * <p>
     * 对齐基准是「名字所属横条的可见区」：镜像侧右对齐，否则左对齐。
     * {@code inset} 用于扣除端头圆占据的宽度（主血条的名字要跳过圆，落在横条正上方）。
     *
     * @param x     血条组左缘
     * @param width 血条组宽度
     * @param inset 可见区起始处相对血条组左缘的内缩量（镜像时作用于右缘）
     * @param scale 字号比例
     */
    private static void drawName(GuiGraphics graphics, Font font, Component name,
                                 int x, int y, int width, boolean mirrored, float scale, int inset) {
        int textWidth = Math.round(font.width(name) * scale);
        int textX = mirrored
                ? x + width - inset - textWidth
                : x + inset;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, name, 0, 0, NAME_COLOR, true);
        graphics.pose().popPose();
    }

    /**
     * 画一条血条。
     * <p>
     * 绘制顺序（后者覆盖前者）：
     * <ol>
     *   <li>中间条（底框，全宽平铺）</li>
     *   <li>高光/纹理层（按血量裁切，不压缩）</li>
     *   <li>端头（始终完整，压在最上层；仅 {@code withHead} 时绘制）</li>
     * </ol>
     * 镜像不靠 pose 翻转（负缩放会反转三角形绕序导致被背面剔除），而是直接计算坐标：
     * 端头固定在屏幕外侧，血条主体向内延伸，填充锚定在端头一侧。
     *
     * @param withHead 主血条为 true；次要血条为 false（纯矩形条）
     * @param mirrored 右侧阵营为 true：端头在右
     */
    private static void drawBar(GuiGraphics graphics, int x, int y, int width,
                                float percent, int color, String style, boolean mirrored, boolean withHead) {
        // 端头比血条高，血条在行内垂直居中
        int barTop = y + (withHead ? (TEX_HEAD_SIZE - TEX_BAR_H) / 2 : 0);

        // 血条主体：从圆心处开始铺入，圆形端头随后盖住左端（右阵营为右端），
        // 这样曲线与矩形的交界由圆形遮挡，不会出现几何缝隙。
        int bodyX;
        int bodyW;
        int headX = 0;
        if (withHead) {
            if (mirrored) {
                headX = x + width - TEX_HEAD_SIZE;
                bodyX = x;
                bodyW = width - TEX_HEAD_CX;
            } else {
                headX = x;
                bodyX = x + TEX_HEAD_CX;
                bodyW = width - TEX_HEAD_CX;
            }
        } else {
            bodyX = x;
            bodyW = width;
        }
        if (bodyW <= 0) return;

        // ---- 1. 中间条（底框，全宽，水平平铺） ----
        blitTiled(graphics, TEX_MID, bodyX, barTop, bodyW, TEX_BAR_H);

        // ---- 2. 高光/纹理层（按血量裁切，不压缩） ----
        int fillW = Math.round(bodyW * Mth.clamp(percent, 0.0f, 1.0f));
        if (fillW > 0) {
            // 填充锚定在端头一侧，血量减少时从远端收缩
            int fillX = mirrored ? bodyX + bodyW - fillW : bodyX;
            setTint(color);
            blitTiled(graphics, fillTexture(style), fillX, barTop, fillW, TEX_BAR_H);
            clearTint();
        }

        // ---- 3. 端头（始终完整，压在最上层，盖住血条内侧端） ----
        if (withHead) {
            setTint(color);
            graphics.blit(TEX_HEAD_OUT, headX, y, 0.0f, 0.0f,
                    TEX_HEAD_SIZE, TEX_HEAD_SIZE, TEX_HEAD_SIZE, TEX_HEAD_SIZE);
            clearTint();
        }
    }

    /**
     * 水平平铺贴图到指定宽度。
     * <p>
     * 不用 {@code blitRepeating}：它按"取纹理中心块再重复"的方式切片，
     * 对有斜纹/网格的填充层会产生接缝。这里从左端起按整块平铺，只裁切最后一块。
     */
    private static void blitTiled(GuiGraphics graphics, ResourceLocation texture,
                                  int x, int y, int width, int height) {
        int drawn = 0;
        while (drawn < width) {
            int w = Math.min(TEX_TILE_W, width - drawn);
            graphics.blit(texture, x + drawn, y, 0.0f, 0.0f, w, height, TEX_TILE_W, height);
            drawn += w;
        }
    }

    /** 设置阵营色染色（黑色描边不受影响：黑 × 色 = 黑）。 */
    private static void setTint(int argb) {
        float r = (argb >> 16 & 0xFF) / 255.0f;
        float g = (argb >> 8 & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        RenderSystem.setShaderColor(r, g, b, 1.0f);
    }

    private static void clearTint() {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** 当前血量百分比，口径与头顶血条一致（getHealth / getMaxHealth）。 */
    private static float healthPercent(LivingEntity entity) {
        float max = entity.getMaxHealth();
        if (max <= 0) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, entity.getHealth() / max));
    }

    // ==================== 扫描与排序 ====================

    private static void scanIfDue(Minecraft mc, List<Scenario.FactionDef> factionDefs) {
        long tick = mc.level.getGameTime();
        if (tick == lastScanTick || tick - lastScanTick < SCAN_INTERVAL_TICKS) return;
        lastScanTick = tick;

        Map<String, List<LivingEntity>> found = new LinkedHashMap<>();
        for (Scenario.FactionDef def : factionDefs) {
            found.put(def.getId(), new ArrayList<>());
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity instanceof Player) continue;

            String factionId = resolveFactionId(entity);
            if (factionId.isEmpty()) continue;

            List<LivingEntity> bucket = found.get(factionId);
            if (bucket != null) {
                bucket.add(living);
            }
        }

        // 按血量上限降序；并列时实体 ID 小的优先，保证排序稳定不抖动
        Comparator<LivingEntity> order = Comparator
                .comparingDouble(LivingEntity::getMaxHealth).reversed()
                .thenComparingInt(Entity::getId);
        for (List<LivingEntity> bucket : found.values()) {
            bucket.sort(order);
        }

        factionMembers.clear();
        factionMembers.putAll(found);

        // 绑定维护：无绑定或绑定实体已不在场 → 取当前排序第一位
        for (Map.Entry<String, List<LivingEntity>> entry : factionMembers.entrySet()) {
            String factionId = entry.getKey();
            List<LivingEntity> members = entry.getValue();
            if (members.isEmpty()) {
                mainBarBinding.remove(factionId);
                continue;
            }
            Integer bound = mainBarBinding.get(factionId);
            if (bound == null || !containsEntityId(members, bound)) {
                mainBarBinding.put(factionId, members.get(0).getId());
            } else if (members.get(0).getId() != bound) {
                // 绑定仍存活：把它提到首位，保证主血条位置不变
                moveToFront(members, bound);
            }
        }
    }

    private static boolean containsEntityId(List<LivingEntity> members, int entityId) {
        for (LivingEntity member : members) {
            if (member.getId() == entityId) return true;
        }
        return false;
    }

    private static void moveToFront(List<LivingEntity> members, int entityId) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getId() == entityId) {
                if (i > 0) {
                    LivingEntity bound = members.remove(i);
                    members.add(0, bound);
                }
                return;
            }
        }
    }

    /** 客户端阵营判定：优先缓存，回退 NBT。 */
    private static String resolveFactionId(Entity entity) {
        String factionId = ClientFactionCache.getCachedFactionId(entity);
        if (factionId == null || factionId.isEmpty()) {
            factionId = FactionHelper.getEntityFactionId(entity);
        }
        return factionId == null ? "" : factionId;
    }

    /** 剧本切换时清空绑定，避免跨剧本残留。 */
    public static void resetBindings() {
        mainBarBinding.clear();
        factionMembers.clear();
        lastScanTick = -1;
    }
}
