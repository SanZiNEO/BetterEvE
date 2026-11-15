package com.sanzineo.bettereve.scenario;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JSON 文件实现的剧本存储。
 *
 * 两个目录：
 * - 预设：scenarios/*.json（项目根目录，随仓库分发，只读种子）
 * - 运行时：config/bettereve/scenarios/*.json（工作副本，游戏与外部编辑器都写这里）
 *
 * 同步规则见 {@link #syncPresetToRuntime}：只在运行时副本缺失、或预设比运行时副本更新时复制；
 * 其余情况一律以运行时副本为准 —— 外部编辑器（douququ 面板）写运行时的改动不会被预设覆盖。
 */
public class JsonScenarioStorage implements ScenarioStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonScenarioStorage.class);

    // 契约：默认 Gson 忽略 JSON 里的未知键 —— 外部编辑器会在剧本里塞自己的键（如 "douququ"），
    // 游戏加载必须照常工作。不要开启 FAIL_ON_UNKNOWN_PROPERTIES，
    // 也不要换成默认严格模式的解析器（Jackson 默认遇到未知键直接报错）。
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path runtimeDir = Paths.get("config", "bettereve", "scenarios");
    private final Path presetDir = Paths.get("scenarios");
    
    @Override
    public List<String> listScenarios() {
        // 合并运行时目录和预设目录的文件列表
        List<String> names = new ArrayList<>();
        names.addAll(listFilesIn(runtimeDir));
        for (String preset : listFilesIn(presetDir)) {
            if (!names.contains(preset)) {
                names.add(preset);
            }
        }
        return names.stream().sorted().collect(Collectors.toList());
    }
    
    @Override
    public Scenario readScenario(String name) throws ScenarioLoadException {
        // 预设 → 运行时同步（仅首次 / 预设更新时，见 syncPresetToRuntime）
        syncPresetToRuntime(name);
        
        // 从运行时读取
        Path runtimeFile = runtimeDir.resolve(name + ".json");
        if (Files.exists(runtimeFile)) {
            return readFromPath(runtimeFile, name);
        }
        throw new ScenarioLoadException("Scenario file not found: " + name);
    }
    
    @Override
    public void writeScenario(Scenario scenario) {
        Path file = getOrCreateRuntimeDir().resolve(scenario.getId() + ".json");
        try {
            String json = GSON.toJson(scenario);
            Files.writeString(file, json);
            LOGGER.info("Written scenario to {}", file);
        } catch (IOException e) {
            LOGGER.error("Failed to write scenario file {}", file, e);
        }
    }
    
    private Scenario readFromPath(Path file, String name) throws ScenarioLoadException {
        if (!Files.exists(file)) {
            throw new ScenarioLoadException("Scenario file not found: " + file);
        }
        try {
            String json = Files.readString(file);
            Scenario scenario = GSON.fromJson(json, Scenario.class);
            if (scenario == null) {
                throw new ScenarioLoadException("Failed to parse scenario (null): " + name);
            }
            if (scenario.getId() == null || scenario.getId().isEmpty()) {
                scenario.setId(name);
            }
            return scenario;
        } catch (JsonSyntaxException e) {
            throw new ScenarioLoadException("Invalid JSON in scenario file " + file + ": " + e.getMessage());
        } catch (IOException e) {
            throw new ScenarioLoadException("Failed to read scenario file " + file + ": " + e.getMessage());
        }
    }
    /**
     * 预设 → 运行时同步。
     *
     * 只在两种情况下复制：
     * 1. 运行时副本缺失（首次加载该剧本）
     * 2. 预设文件比运行时副本更新（mtime 更晚）—— 仓库里改了预设，重新加载即可生效
     *
     * 运行时副本更新（或同刻）时不动：外部编辑器直接写运行时目录，
     * 它的写入必须活过下一次加载（"始终覆盖"会让面板的调参在加载瞬间全部丢失）。
     */
    private void syncPresetToRuntime(String name) {
        Path presetFile = presetDir.resolve(name + ".json");
        if (!Files.exists(presetFile)) return;
        Path runtimeFile = getOrCreateRuntimeDir().resolve(name + ".json");
        try {
            if (Files.exists(runtimeFile)
                    && Files.getLastModifiedTime(runtimeFile).compareTo(Files.getLastModifiedTime(presetFile)) >= 0) {
                return;
            }
            Files.copy(presetFile, runtimeFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Synced preset scenario {} to runtime directory", name);
        } catch (IOException e) {
            LOGGER.error("Failed to sync preset scenario {}", name, e);
        }
    }
    
    private Path getOrCreateRuntimeDir() {
        try {
            Files.createDirectories(runtimeDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create scenario directory {}", runtimeDir, e);
        }
        return runtimeDir;
    }
    
    private List<String> listFilesIn(Path dir) {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
