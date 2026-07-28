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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scenario JSON 读写。
 * 文件位置：config/bettereve/scenarios/*.json
 */
public class ScenarioIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioIO.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SCENARIO_DIR = Paths.get("config", "bettereve", "scenarios");
    private static final Path PRESET_DIR = Paths.get("scenarios"); // 项目根目录预设

    /**
     * 获取预设剧本目录。
     */
    public static Path getPresetDir() {
        return PRESET_DIR;
    }

    /**
     * 从预设目录复制剧本到运行时目录。
     */
    public static void copyPresetToRuntime(String name) {
        Path presetFile = PRESET_DIR.resolve(name + ".json");
        Path runtimeFile = getScenarioDir().resolve(name + ".json");
        if (Files.exists(presetFile) && !Files.exists(runtimeFile)) {
            try {
                Files.createDirectories(getScenarioDir());
                Files.copy(presetFile, runtimeFile);
                LOGGER.info("Copied preset scenario {} to runtime directory", name);
            } catch (IOException e) {
                LOGGER.error("Failed to copy preset scenario {}", name, e);
            }
        }
    }

    /**
     * 读取指定剧本 — 优先运行时目录，其次预设目录。
     */
    public static Scenario readScenario(String name) throws ScenarioLoadException {
        // 先尝试运行时目录
        Path runtimeFile = SCENARIO_DIR.resolve(name + ".json");
        if (Files.exists(runtimeFile)) {
            return readFromPath(runtimeFile, name);
        }
        // 再尝试预设目录
        Path presetFile = PRESET_DIR.resolve(name + ".json");
        if (Files.exists(presetFile)) {
            // 复制到运行时目录
            copyPresetToRuntime(name);
            return readFromPath(presetFile, name);
        }
        throw new ScenarioLoadException("Scenario file not found: " + name);
    }

    private static Scenario readFromPath(Path file, String name) throws ScenarioLoadException {
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
     * 获取剧本目录，如果不存在则创建。
     */
    public static Path getScenarioDir() {
        try {
            Files.createDirectories(SCENARIO_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create scenario directory: {}", SCENARIO_DIR, e);
        }
        return SCENARIO_DIR;
    }

    /**
     * 列出所有可用剧本（不含 .json 后缀的文件名）。
     */
    public static List<String> listScenarios() {
        Path dir = getScenarioDir();
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.error("Failed to list scenarios", e);
            return new ArrayList<>();
        }
    }

    /**
     * 写入剧本到 JSON 文件。
     */
    public static void writeScenario(Scenario scenario) {
        Path file = getScenarioDir().resolve(scenario.getId() + ".json");
        try {
            String json = GSON.toJson(scenario);
            Files.writeString(file, json);
            LOGGER.info("Written scenario to {}", file);
        } catch (IOException e) {
            LOGGER.error("Failed to write scenario file {}", file, e);
        }
    }

    /**
     * 创建一个默认的示例剧本。
     */
    public static void createDefaultScenario() {
        Scenario scenario = new Scenario();
        scenario.setId("default");
        scenario.setName("Default");
        scenario.setDescription("默认剧本 — 红蓝两队对战");

        List<Scenario.FactionDef> factions = new ArrayList<>();
        factions.add(new Scenario.FactionDef("neutral", "中立", "#FFFFFF"));
        factions.add(new Scenario.FactionDef("red", "红队", "#FF0000"));
        factions.add(new Scenario.FactionDef("blue", "蓝队", "#0000FF"));
        scenario.setFactions(factions);

        List<Scenario.RelationDef> relations = new ArrayList<>();
        relations.add(new Scenario.RelationDef("red", "blue", "hostile"));
        relations.add(new Scenario.RelationDef("blue", "red", "hostile"));
        relations.add(new Scenario.RelationDef("*", "neutral", "neutral"));
        scenario.setRelations(relations);

        Scenario.ScenarioOptions options = new Scenario.ScenarioOptions();
        options.setPlayersDefaultFaction("neutral");
        scenario.setOptions(options);

        writeScenario(scenario);
    }
}
