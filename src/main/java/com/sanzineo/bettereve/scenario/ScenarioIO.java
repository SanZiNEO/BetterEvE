package com.sanzineo.bettereve.scenario;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Scenario I/O 门面 — 所有剧本来读写通过此门面。
 * 
 * 默认使用 JsonScenarioStorage（JSON 文件）。
 * 后续需要加密/二进制存储时，只需：
 *   ScenarioIO.setStorage(new EncryptedScenarioStorage(key));
 * 所有调用方代码不变。
 */
public class ScenarioIO {
    private static ScenarioStorage storage = new JsonScenarioStorage();
    
    public static void setStorage(ScenarioStorage newStorage) {
        storage = newStorage;
    }
    
    public static ScenarioStorage getStorage() {
        return storage;
    }
    
    public static List<String> listScenarios() {
        return storage.listScenarios();
    }
    
    public static Scenario readScenario(String name) throws ScenarioLoadException {
        return storage.readScenario(name);
    }
    
    public static void writeScenario(Scenario scenario) {
        storage.writeScenario(scenario);
    }
    
    public static Path getScenarioDir() {
        return Paths.get("config", "bettereve", "scenarios");
    }
    
    public static Path getPresetDir() {
        return Paths.get("scenarios");
    }
    
}
