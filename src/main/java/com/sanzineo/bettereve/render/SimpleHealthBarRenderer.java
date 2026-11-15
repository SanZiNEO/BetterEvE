package com.sanzineo.bettereve.render;

import com.sanzineo.bettereve.entity.CrystalEntity;
import com.sanzineo.bettereve.entity.CrystalEntityModel;
import com.sanzineo.bettereve.faction.ClientFactionCache;
import com.sanzineo.bettereve.faction.FactionHelper;
import com.sanzineo.bettereve.entitylv.EntityLevelSystem;
import com.sanzineo.bettereve.network.HealthBarTextVisibilityPacket;
import com.sanzineo.bettereve.scenario.Scenario;
import com.sanzineo.bettereve.scenario.ScenarioManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 世界空间实体化血条渲染器。
 * <p>
 * 参考 Neat 的做法：在 LevelRenderer.renderEntity 的实体渲染完成后，
 * 以世界坐标 + 摄像机朝向绘制 billboard 血条，使 Oculus/Iris 光影能正常处理。
 * 视觉布局保留旧版：背景层 + 填充层 + 容器层 + Lv 文字 + 血量数字。
 */
public class SimpleHealthBarRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEvE/HealthBar");

    private static final float BAR_TOTAL_WIDTH = 80.0f;
    private static final float BAR_TOTAL_HEIGHT = 10.0f;

    private static final float HALF_WIDTH = BAR_TOTAL_WIDTH / 2.0f;
    private static final float HALF_HEIGHT = BAR_TOTAL_HEIGHT / 2.0f;

    private static final float PRE_BAR_LEFT = 0 - HALF_WIDTH;
    private static final float PRE_BAR_RIGHT = 0 + HALF_WIDTH;
    private static final float PRE_BAR_BOTTOM = 0 - HALF_HEIGHT;
    private static final float PRE_BAR_TOP = 0 + HALF_HEIGHT;

    // 容器层尺寸（是基础尺寸的 1.2 倍）
    private static final float CONTAINER_BAR_TOTAL_WIDTH = BAR_TOTAL_WIDTH * 1.2f;
    private static final float CONTAINER_BAR_TOTAL_HEIGHT = BAR_TOTAL_HEIGHT * 1.2f;

    private static final float CONTAINER_HALF_WIDTH = CONTAINER_BAR_TOTAL_WIDTH / 2.0f;
    private static final float CONTAINER_HALF_HEIGHT = CONTAINER_BAR_TOTAL_HEIGHT / 2.0f;

    private static final float PRE_CONTAINER_BAR_LEFT = 0 - CONTAINER_HALF_WIDTH;
    private static final float PRE_CONTAINER_BAR_RIGHT = 0 + CONTAINER_HALF_WIDTH;
    private static final float PRE_CONTAINER_BAR_BOTTOM = 0 - CONTAINER_HALF_HEIGHT;
    private static final float PRE_CONTAINER_BAR_TOP = 0 + CONTAINER_HALF_HEIGHT;

    /** 血条头顶偏移（方块） */
    private static final float BAR_Y_OFFSET = 0.5f;

    /** 世界空间缩放基准：把 80 单位宽的旧 HUD 血条换算成约 2.1 格宽的世界血条 */
    private static final float WORLD_SCALE_BASE = 0.0267f;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    // 统一缩放因子，由剧本 options.healthBarScale 控制
    private static final float DEFAULT_SCALE = 1.0f;

    // 每帧已渲染实体缓存，防止同一实体被多次绘制
    private static final Set<UUID> renderedThisFrame = new HashSet<>();
    private static final Map<UUID, Float> healthCache = new HashMap<>();

    // Oculus/Iris 阴影 pass 检测（反射调用，避免硬依赖）
    private static final Method IRIS_API_GET_INSTANCE;
    private static final Method IRIS_IS_RENDERING_SHADOW_PASS;

    static {
        Method getInstance = null;
        Method isRenderingShadowPass = null;
        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstance = irisApi.getMethod("getInstance");
            isRenderingShadowPass = irisApi.getMethod("isRenderingShadowPass");
        } catch (Throwable ignored) {
            // Oculus/Iris 未安装时保持无影响
        }
        IRIS_API_GET_INSTANCE = getInstance;
        IRIS_IS_RENDERING_SHADOW_PASS = isRenderingShadowPass;
    }

    /** 是否正处于 Oculus/Iris 的阴影 pass；非 Oculus 环境始终 false。 */
    private static boolean isRenderingShadowPass() {
        if (IRIS_API_GET_INSTANCE == null || IRIS_IS_RENDERING_SHADOW_PASS == null) {
            return false;
        }
        try {
            Object api = IRIS_API_GET_INSTANCE.invoke(null);
            return Boolean.TRUE.equals(IRIS_IS_RENDERING_SHADOW_PASS.invoke(api));
        } catch (Throwable t) {
            return false;
        }
    }

    public static void onFrameStart() {
        renderedThisFrame.clear();
    }

    /** 当前血条缩放：读剧本 options.healthBarScale，剧本未加载时回退 1.0。 */
    public static float getGlobalScale() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario == null || scenario.getOptions() == null) {
            return DEFAULT_SCALE;
        }
        float scale = scenario.getOptions().getHealthBarScale();
        return Math.max(0.1f, Math.min(3.0f, scale));
    }

    public static boolean shouldShowHealthBar(Entity entity) {
        if (entity == null || !(entity instanceof LivingEntity)) {
            // 诊断：投掷物若本不该有血条却出现了，说明它其实是 LivingEntity 子类。
            // ProjectileSlashEntity 这类"名字像投射物、实际是 Mob"的实体就会走到这里。
            if (entity != null && entity instanceof net.minecraft.world.entity.projectile.Projectile) {
                LOGGER.debug("[血条] 投掷物被判定为无血条: {}", entity.getType());
            }
            return false;
        }
        if (entity instanceof Player) {
            return false;
        }
        // 剧本总开关：与格斗血条相互独立，关闭时所有实体头顶血条一律不渲染
        var scenario = ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getOptions() != null
                && !scenario.getOptions().isOverheadHealthBar()) {
            return false;
        }
        boolean visible = com.sanzineo.bettereve.network.HealthBarVisibilityPacket.ClientHealthBarVisibilityCache
                .getEntityHealthBarVisibility(entity.getUUID());
        // 诊断：打印非 Mob 的 LivingEntity（这类最容易意外显示血条）
        if (debugLogged.add(entity.getUUID())) {
            LOGGER.info("[血条] {} type={} 是Mob={} 是投射物={} 显示={}",
                    entity.getName().getString(), entity.getType(),
                    entity instanceof net.minecraft.world.entity.Mob,
                    entity instanceof net.minecraft.world.entity.projectile.Projectile,
                    visible);
        }
        return visible;
    }

    /** 诊断用：每个实体只打印一次，避免刷屏 */
    private static final java.util.Set<java.util.UUID> debugLogged =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 世界空间血条渲染入口，由 LevelRendererMixin 在实体渲染完成后调用。
     */
    public static void renderHealthBarWorld(Entity entity, PoseStack poseStack,
                                            MultiBufferSource bufferSource, Camera camera,
                                            float partialTick, double camX, double camY, double camZ) {
        if (!shouldShowHealthBar(entity)) {
            return;
        }

        // 阴影 pass 中既不要绘制也不要标记本帧已渲染，
        // 否则 Oculus/Iris 会先渲染近处实体，导致主视角血条被跳过。
        if (isRenderingShadowPass()) {
            return;
        }

        UUID entityId = entity.getUUID();
        if (renderedThisFrame.contains(entityId)) {
            return;
        }
        renderedThisFrame.add(entityId);

        LivingEntity living = (LivingEntity) entity;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.font == null) {
            return;
        }

        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        float healthPercent = getHealthPercent(living);
        int factionColor = getFactionColor(entity);
        int level = getEntityLevel(entity);
        boolean showLevelText = shouldShowLevelText(entity, level);
        boolean showHealthText = shouldShowHealthText(entity);

        String levelText = showLevelText ? "Lv." + level : "";
        String healthText = showHealthText ? (int) Math.ceil(living.getHealth()) + "/" + (int) Math.ceil(living.getMaxHealth()) : "";

        float finalScale = getGlobalScale() * WORLD_SCALE_BASE;

        // 普通生物：血条在碰撞箱正上方。
        // 基地实体：碰撞箱在底部、模型在碰撞箱上方，血条要再抬高“模型高度 × modelScale”。
        double renderY;
        if (living instanceof CrystalEntity crystal) {
            renderY = y + living.getBbHeight()
                    + CrystalEntityModel.getModelHeightAboveCollisionBox() * crystal.getModelScale()
                    + BAR_Y_OFFSET;
        } else {
            renderY = y + living.getBbHeight() + BAR_Y_OFFSET;
        }

        poseStack.pushPose();
        try {
            poseStack.translate(x - camX, renderY - camY, z - camZ);
            poseStack.mulPose(camera.rotation());
            // 负 X/Y 缩放让血条面向玩家且不镜像
            poseStack.scale(-finalScale, -finalScale, finalScale);

            renderBarBackground(poseStack, bufferSource, factionColor);
            renderHealthFill(poseStack, bufferSource, healthPercent, factionColor);
            renderBarContainer(poseStack, bufferSource);

            if (showLevelText && !levelText.isEmpty()) {
                renderLevelText(poseStack, bufferSource, levelText);
            }
            if (showHealthText && !healthText.isEmpty()) {
                renderHealthText(poseStack, bufferSource, healthText);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static float getHealthPercent(LivingEntity entity) {
        UUID entityId = entity.getUUID();

        if (!healthCache.containsKey(entityId)) {
            healthCache.put(entityId, entity.getHealth());
        }

        float previousHealth = healthCache.get(entityId);
        float currentHealth = entity.getHealth();

        if (previousHealth != currentHealth) {
            float interpolatedHealth = previousHealth + (currentHealth - previousHealth) * 0.1f;
            healthCache.put(entityId, interpolatedHealth);

            if (Math.abs(interpolatedHealth - currentHealth) < 0.1f) {
                healthCache.put(entityId, currentHealth);
                interpolatedHealth = currentHealth;
            }

            currentHealth = interpolatedHealth;
        }

        float maxHealth = entity.getMaxHealth();
        return maxHealth <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, currentHealth / maxHealth));
    }

    /**
     * 从剧本 faction.color 读取阵营颜色；读取失败时回退到默认色。
     */
    private static int getFactionColor(Entity entity) {
        String factionId = ClientFactionCache.getCachedFactionId(entity);
        if (factionId == null || factionId.isEmpty()) {
            factionId = FactionHelper.getEntityFactionId(entity);
        }

        // 无阵营 → 白色
        if (factionId == null || factionId.isEmpty()) {
            return 0xFFFFFFFF;
        }

        // 只认剧本里的阵营 color；写入什么颜色就用什么颜色，没有兜底
        Scenario scenario = ScenarioManager.getActiveScenario();
        if (scenario != null && scenario.getFactions() != null) {
            for (Scenario.FactionDef def : scenario.getFactions()) {
                if (factionId.equals(def.getId())) {
                    return 0xFF000000 | def.getColorInt();
                }
            }
        }

        // 剧本里没有这个阵营 → 视为无阵营 → 白色
        return 0xFFFFFFFF;
    }

    /**
     * 背景色 = 阵营颜色的同色系加深版（稍微深一点，用来垫底显示背景效果）。
     * bar_background_texture.png 和 bar_texture.png 都是纯白图片，靠顶点颜色染色。
     */
    private static int getBackgroundColor(int factionColor) {
        int r = (factionColor >> 16) & 0xFF;
        int g = (factionColor >> 8) & 0xFF;
        int b = factionColor & 0xFF;
        int a = (factionColor >> 24) & 0xFF;

        r = (int) (r * 0.7f);
        g = (int) (g * 0.7f);
        b = (int) (b * 0.7f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int getEntityLevel(Entity entity) {
        try {
            if (entity instanceof LivingEntity) {
                if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
                    int cachedLevel = com.sanzineo.bettereve.network.LevelSyncPacket.ClientLevelCache.getEntityLevel(entity.getUUID());
                    if (cachedLevel > 0) {
                        return cachedLevel;
                    }
                }
                return EntityLevelSystem.getEntityLevel((LivingEntity) entity);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean shouldShowLevelText(Entity entity, int level) {
        // 无等级不显示
        if (level <= 0) return false;
        // 基地默认不显示 Lv（自有血条）
        if (entity instanceof CrystalEntity) return false;
        // 优先级：生物 NBT（已同步进客户端缓存）> 剧本 healthBarLevelText
        UUID uuid = entity.getUUID();
        if (HealthBarTextVisibilityPacket.ClientHealthBarTextVisibilityCache.hasData(uuid)) {
            return HealthBarTextVisibilityPacket.ClientHealthBarTextVisibilityCache.getEntityLevelTextVisibility(uuid);
        }
        return scenarioLevelText();
    }

    private static boolean shouldShowHealthText(Entity entity) {
        // 优先级：生物 NBT（已同步进客户端缓存）> 剧本 healthBarHealthText
        UUID uuid = entity.getUUID();
        if (HealthBarTextVisibilityPacket.ClientHealthBarTextVisibilityCache.hasData(uuid)) {
            return HealthBarTextVisibilityPacket.ClientHealthBarTextVisibilityCache.getEntityHealthTextVisibility(uuid);
        }
        return scenarioHealthText();
    }

    /** 剧本 healthBarLevelText；剧本未加载时回退 true。 */
    private static boolean scenarioLevelText() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        return scenario == null || scenario.getOptions() == null
                || scenario.getOptions().isHealthBarLevelText();
    }

    /** 剧本 healthBarHealthText；剧本未加载时回退 true。 */
    private static boolean scenarioHealthText() {
        Scenario scenario = ScenarioManager.getActiveScenario();
        return scenario == null || scenario.getOptions() == null
                || scenario.getOptions().isHealthBarHealthText();
    }

    private static void renderHealthFill(PoseStack poseStack, MultiBufferSource bufferSource,
                                         float healthPercent, int factionColor) {
        if (healthPercent <= 0.0f) {
            return;
        }

        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.FILL_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        float fillRight = PRE_BAR_LEFT + (BAR_TOTAL_WIDTH * healthPercent);
        int r = (factionColor >> 16) & 0xFF;
        int g = (factionColor >> 8) & 0xFF;
        int b = factionColor & 0xFF;
        int a = (factionColor >> 24) & 0xFF;
        // 世界空间：-Z 朝摄像机（靠前），+Z 远离摄像机（靠后）
        // 从后到前：背景(+0.1) → 填充(0.0) → 容器(-0.1) → 文字(-0.2)
        float fillZ = 0.0f;

        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_BOTTOM, fillZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, fillRight, PRE_BAR_BOTTOM, fillZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, fillRight, PRE_BAR_TOP, fillZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_TOP, fillZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }

    private static void renderBarBackground(PoseStack poseStack, MultiBufferSource bufferSource,
                                            int factionColor) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.BACKGROUND_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        int bgColor = getBackgroundColor(factionColor);
        int r = (bgColor >> 16) & 0xFF;
        int g = (bgColor >> 8) & 0xFF;
        int b = bgColor & 0xFF;
        int a = (bgColor >> 24) & 0xFF;
        // 最底层（远离摄像机）
        float backgroundZ = 0.1f;

        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_BOTTOM, backgroundZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_RIGHT, PRE_BAR_BOTTOM, backgroundZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_RIGHT, PRE_BAR_TOP, backgroundZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_BAR_LEFT, PRE_BAR_TOP, backgroundZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }

    private static void renderBarContainer(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(HealthBarRenderType.CONTAINER_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        int r = 255;
        int g = 255;
        int b = 255;
        int a = 255;
        // 最上层套壳（朝向摄像机，带竖线/格子）
        float containerZ = -0.1f;

        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_LEFT, PRE_CONTAINER_BAR_BOTTOM, containerZ)
            .color(r, g, b, a)
            .uv(0, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_RIGHT, PRE_CONTAINER_BAR_BOTTOM, containerZ)
            .color(r, g, b, a)
            .uv(1, 0)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_RIGHT, PRE_CONTAINER_BAR_TOP, containerZ)
            .color(r, g, b, a)
            .uv(1, 1)
            .uv2(0xF000F0)
            .endVertex();
        vertexConsumer.vertex(matrix, PRE_CONTAINER_BAR_LEFT, PRE_CONTAINER_BAR_TOP, containerZ)
            .color(r, g, b, a)
            .uv(0, 1)
            .uv2(0xF000F0)
            .endVertex();
    }

    private static void renderLevelText(PoseStack poseStack, MultiBufferSource bufferSource, String levelText) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        float textScale = 1.0f;
        float levelTextScaledHeight = font.lineHeight * textScale;
        float levelTextRawWidth = font.width(levelText);

        float x = 0 - HALF_WIDTH * 0.98f;
        float y = 0 - levelTextScaledHeight * 0.5f;

        poseStack.pushPose();
        try {
            // 文字放在最上层（比容器更靠近摄像机）
            poseStack.translate(0, 0, -0.2f);
            poseStack.scale(textScale, textScale, textScale);
            font.drawInBatch(levelText,
                x / textScale, y / textScale,
                TEXT_COLOR, false,
                poseStack.last().pose(), bufferSource,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderHealthText(PoseStack poseStack, MultiBufferSource bufferSource,
                                         String healthText) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        float textScale = 0.60f;
        float healthTextScaledHeight = font.lineHeight * textScale;
        float healthTextRawWidth = font.width(healthText);
        float healthTextScaledWidth = healthTextRawWidth * textScale;

        float x = 0 + HALF_WIDTH * 0.98f - healthTextScaledWidth * 0.98f;
        float y = 0 - healthTextScaledHeight * 0.5f;

        poseStack.pushPose();
        try {
            // 文字放在最上层（比容器更靠近摄像机）
            poseStack.translate(0, 0, -0.2f);
            poseStack.scale(textScale, textScale, textScale);
            font.drawInBatch(healthText,
                x / textScale, y / textScale,
                TEXT_COLOR, false,
                poseStack.last().pose(), bufferSource,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } finally {
            poseStack.popPose();
        }
    }
}
