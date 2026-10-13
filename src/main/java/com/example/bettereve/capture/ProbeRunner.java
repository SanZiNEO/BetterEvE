package com.example.bettereve.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P0 探针：把一只实体在渲染循环之外渲染，收下顶点几何 + 贴图，落盘成 model.json / anim.json。
 * <p>
 * 全部前置按计划 §02 §5：player/level 非空、{@code dispatcher.prepare(...)}、
 * {@code overrideCameraOrientation(单位四元数)}、{@code setCustomName(null)}、朝向字段清零。
 * <p>
 * 动画两条路：
 * <ul>
 *   <li>{@code walk}：推 {@link LivingEntity#walkAnimation}（闭式解，20 帧无缝）</li>
 *   <li>关键帧动画：驱动实体自己的 public {@link AnimationState}（如监守者的 6 段）</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class ProbeRunner {

    /** 从 RenderType.toString() 里抠出贴图 ResourceLocation（计划 §02 §3）。 */
    private static final Pattern TEXTURE = Pattern.compile("([a-z0-9_.\\-]+:[a-z0-9_./\\-]+\\.png)");

    /** walkAnimation 一圈 20 帧的无缝速度：2π / (0.6662 * 20)。 */
    private static final float WALK_SPEED = (float) (2.0 * Math.PI / (0.6662 * 20.0));

    private static final int WALK_FRAMES = 20;
    private static final int FPS = 20;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Gson GSON = new GsonBuilder().create();

    private ProbeRunner() {
    }

    /** 一帧的捕获结果：RenderType → 顶点列表（每个顶点 x,y,z,u,v,r,g,b,a）。 */
    private record Frame(Map<RenderType, List<float[]>> groups) {
    }

    /** 一段动画：状态名 + 逐帧几何 + 时长。 */
    private record Anim(String state, List<Frame> frames, boolean loop, float durationSec) {
    }

    public static String probe(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return "no level";
        }
        if (mc.player == null) {
            return "no player";
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            return "unknown entity type: " + id;
        }

        Entity entity;
        try {
            entity = type.create(level);
        } catch (Throwable t) {
            return "create() threw: " + t;
        }
        if (entity == null) {
            return "create() returned null (PLAYER / feature flag off / no factory)";
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher.camera == null) {
            return "camera not ready (world not rendered yet)";
        }
        EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
        if (renderer == null) {
            return "no renderer for " + id;
        }

        // ── 摆位 + 清朝向（计划 §02 §5「朝向双管齐下」）
        var p = mc.player.position();
        entity.setPos(p.x, p.y, p.z);
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        entity.yRotO = 0.0F;
        entity.xRotO = 0.0F;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = 0.0F;
            living.yBodyRotO = 0.0F;
            living.yHeadRot = 0.0F;
            living.yHeadRotO = 0.0F;
        }
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);

        // ── 渲染前置
        dispatcher.prepare(level, dispatcher.camera, entity);
        dispatcher.overrideCameraOrientation(new Quaternionf());

        Frame idle = renderFrame(mc, dispatcher, entity);

        List<Anim> anims = new ArrayList<>();

        // walk：推 walkAnimation，一圈 20 帧
        if (entity instanceof LivingEntity living) {
            List<Frame> walk = new ArrayList<>(WALK_FRAMES);
            for (int f = 0; f < WALK_FRAMES; f++) {
                living.walkAnimation.update(WALK_SPEED, 1.0F);
                walk.add(renderFrame(mc, dispatcher, entity));
            }
            anims.add(new Anim("walk", walk, true, WALK_FRAMES / (float) FPS));
        }

        // 监守者：6 段关键帧动画（public AnimationState，直接驱动）
        if (entity instanceof Warden warden) {
            anims.add(captureState(mc, dispatcher, warden, "emerge", warden.emergeAnimationState, 6.68F));
            anims.add(captureState(mc, dispatcher, warden, "digging", warden.diggingAnimationState, 5.0F));
            anims.add(captureState(mc, dispatcher, warden, "roar", warden.roarAnimationState, 4.2F));
            anims.add(captureState(mc, dispatcher, warden, "sniff", warden.sniffAnimationState, 4.16F));
            anims.add(captureState(mc, dispatcher, warden, "sonic_boom", warden.sonicBoomAnimationState, 3.0F));
            anims.add(captureState(mc, dispatcher, warden, "attack", warden.attackAnimationState, 0.34F));
        }

        dispatcher.prepare(level, dispatcher.camera, mc.player);

        // ── 写盘
        Path dir = FMLPaths.GAMEDIR.get().resolve("bettereve-dump").resolve("probe").resolve(id.getPath());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return "mkdir failed: " + e;
        }

        Map<ResourceLocation, Integer> textureIds = new LinkedHashMap<>();
        List<JsonObject> textureJson = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        summary.append("registry=").append(id).append('\n');

        JsonObject model = new JsonObject();
        model.addProperty("format", "bettereve-model/1");
        model.addProperty("registry", id.toString());
        model.addProperty("space", "entity");
        model.addProperty("unit", "block");
        JsonObject frameObj = new JsonObject();
        frameObj.addProperty("state", "idle");
        frameObj.addProperty("t", 0.0);
        model.add("frame", frameObj);
        model.add("groups", buildGroups(mc, idle, entity, renderer, dir, textureIds, textureJson, summary, "idle"));

        JsonArray texArray = new JsonArray();
        for (JsonObject t : textureJson) {
            texArray.add(t);
        }
        model.add("textures", texArray);

        int idleQuads = 0;
        for (JsonObject g : jsonObjects(model.getAsJsonArray("groups"))) {
            idleQuads += g.getAsJsonArray("quads").size();
        }
        summary.append("idle quads=").append(idleQuads)
                .append(" groups=").append(model.getAsJsonArray("groups").size())
                .append(" textures=").append(textureJson.size()).append('\n');

        try {
            Files.writeString(dir.resolve(id.getPath() + ".model.json"), GSON.toJson(model), StandardCharsets.UTF_8);

            for (Anim anim : anims) {
                JsonObject a = new JsonObject();
                a.addProperty("format", "bettereve-anim/1");
                a.addProperty("registry", id.toString());
                a.addProperty("state", anim.state());
                a.addProperty("space", "entity");
                a.addProperty("unit", "block");
                a.addProperty("loop", anim.loop());
                a.addProperty("fps", FPS);
                a.addProperty("duration", anim.durationSec());
                JsonArray keyframes = new JsonArray();
                List<Frame> frames = anim.frames();
                for (int f = 0; f < frames.size(); f++) {
                    JsonObject kf = new JsonObject();
                    kf.addProperty("t", f / (float) FPS);
                    kf.add("groups", buildGroups(mc, frames.get(f), entity, renderer, dir,
                            textureIds, textureJson, summary, anim.state() + "#" + f));
                    keyframes.add(kf);
                }
                a.add("keyframes", keyframes);
                JsonArray ta = new JsonArray();
                for (JsonObject t : textureJson) {
                    ta.add(t);
                }
                a.add("textures", ta);
                Files.writeString(dir.resolve(id.getPath() + "." + anim.state() + ".anim.json"),
                        GSON.toJson(a), StandardCharsets.UTF_8);
                summary.append("anim ").append(anim.state()).append(": ").append(frames.size())
                        .append(" frames / ").append(anim.durationSec()).append("s\n");
            }

            Files.writeString(dir.resolve("probe.txt"), summary.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "write failed: " + e;
        }

        return summary.toString().replace('\n', ' ');
    }

    // ──────────────────────────────────────────────────────────────

    /** 驱动一个 {@link AnimationState} 并逐帧捕获。 */
    private static Anim captureState(Minecraft mc, EntityRenderDispatcher dispatcher, Entity entity,
                                     String name, AnimationState state, float durationSec) {
        state.start(entity.tickCount);
        int frames = Math.max(2, Math.round(durationSec * FPS));
        List<Frame> out = new ArrayList<>(frames);
        for (int i = 0; i < frames; i++) {
            entity.tickCount++;                     // ageInTicks = tickCount + partialTick → 动画时间前进
            out.add(renderFrame(mc, dispatcher, entity));
        }
        state.stop();
        return new Anim(name, out, false, durationSec);
    }

    private static Frame renderFrame(Minecraft mc, EntityRenderDispatcher dispatcher, Entity entity) {
        CaptureBuffer buffer = new CaptureBuffer();
        @SuppressWarnings({"rawtypes", "unchecked"})
        EntityRenderer raw = dispatcher.getRenderer(entity);
        raw.render(entity, 0.0F, 1.0F, new PoseStack(), buffer, FULL_BRIGHT);
        Map<RenderType, List<float[]>> copy = new LinkedHashMap<>();
        for (var e : buffer.buffers().entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue().vertices));
        }
        return new Frame(copy);
    }

    /** 把一帧的分组写成 JSON，并顺手把贴图导出。 */
    private static JsonArray buildGroups(Minecraft mc, Frame frame, Entity entity,
                                         @SuppressWarnings("rawtypes") EntityRenderer renderer,
                                         Path dir, Map<ResourceLocation, Integer> textureIds,
                                         List<JsonObject> textureJson, StringBuilder summary, String label) {
        JsonArray groups = new JsonArray();
        for (var entry : frame.groups().entrySet()) {
            RenderType type = entry.getKey();
            List<float[]> verts = entry.getValue();
            if (verts.isEmpty()) {
                continue;
            }
            VertexFormat.Mode mode = type.mode();
            JsonArray quads = new JsonArray();
            if (mode == VertexFormat.Mode.QUADS) {
                for (int i = 0; i + 3 < verts.size(); i += 4) {
                    quads.add(quad(verts, i));
                }
            }

            ResourceLocation texture = resolveTexture(type);
            String source = texture != null ? "renderType" : "fallback";
            if (texture == null) {
                try {
                    texture = renderer.getTextureLocation(entity);
                } catch (Throwable ignored) {
                    texture = null;
                }
            }
            if (texture != null && !textureIds.containsKey(texture)) {
                int idx = textureIds.size();
                textureIds.put(texture, idx);
                exportTexture(mc, texture, dir);
                JsonObject t = new JsonObject();
                t.addProperty("id", idx);
                t.addProperty("location", texture.toString());
                t.addProperty("file", texture.getNamespace() + "/" + texture.getPath());
                textureJson.add(t);
            }

            JsonObject g = new JsonObject();
            g.addProperty("texture", texture == null ? -1 : textureIds.get(texture));
            g.addProperty("textureSource", source);
            g.addProperty("renderType", type.toString());
            g.addProperty("mode", mode.name());
            g.add("quads", quads);
            groups.add(g);

            summary.append("  [").append(label).append("] ").append(mode.name())
                    .append(" verts=").append(verts.size())
                    .append(" quads=").append(quads.size())
                    .append(" tex=").append(texture).append('\n');
        }
        return groups;
    }

    private static JsonObject quad(List<float[]> verts, int i) {
        JsonObject q = new JsonObject();
        JsonArray pos = new JsonArray();
        JsonArray uv = new JsonArray();
        for (int k = 0; k < 4; k++) {
            float[] v = verts.get(i + k);
            JsonArray p3 = new JsonArray();
            p3.add(round(v[0]));
            p3.add(round(v[1]));
            p3.add(round(v[2]));
            pos.add(p3);
            JsonArray p2 = new JsonArray();
            p2.add(round(v[3]));
            p2.add(round(v[4]));
            uv.add(p2);
        }
        q.add("pos", pos);
        q.add("uv", uv);
        return q;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /** 从 RenderType.toString() 里抠贴图；抠不到返回 null。 */
    private static ResourceLocation resolveTexture(RenderType type) {
        try {
            Matcher m = TEXTURE.matcher(type.toString());
            if (m.find()) {
                return new ResourceLocation(m.group(1));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void exportTexture(Minecraft mc, ResourceLocation texture, Path dir) {
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(texture);
            if (res.isEmpty()) {
                return;
            }
            Path out = dir.resolve(texture.getNamespace()).resolve(texture.getPath());
            Files.createDirectories(out.getParent());
            try (InputStream in = res.get().open()) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {
        }
    }

    private static List<JsonObject> jsonObjects(JsonArray array) {
        List<JsonObject> out = new ArrayList<>();
        array.forEach(e -> out.add(e.getAsJsonObject()));
        return out;
    }
}
