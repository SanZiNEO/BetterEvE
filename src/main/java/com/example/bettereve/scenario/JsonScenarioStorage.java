package com.example.bettereve.scenario;

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
 * 文件位置：config/bettereve/scenarios/*.json（运行时）
 * 预设文件：scenarios/*.json（项目根目录，游戏外编辑）
 */
public class JsonScenarioStorage implements ScenarioStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonScenarioStorage.class);
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
        // 始终从预设同步到运行时
        copyPresetToRuntime(name);
        
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
    private void copyPresetToRuntime(String name) {
        Path presetFile = presetDir.resolve(name + ".json");
        Path runtimeFile = getOrCreateRuntimeDir().resolve(name + ".json");
        if (Files.exists(presetFile)) {
            try {
                Files.copy(presetFile, runtimeFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Copied preset scenario {} to runtime directory", name);
            } catch (IOException e) {
                LOGGER.error("Failed to copy preset scenario {}", name, e);
            }
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
