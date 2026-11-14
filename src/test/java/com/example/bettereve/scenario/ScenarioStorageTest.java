package com.example.bettereve.scenario;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剧本存储门面测试：默认后端、目录约定、预设列表与缺失剧本的异常。
 * 不写入仓库（不调用 writeScenario / 不读取会触发同步的预设）。
 */
class ScenarioStorageTest {

    @Test
    void defaultStorageIsJsonBacked() {
        assertInstanceOf(JsonScenarioStorage.class, ScenarioIO.getStorage());
    }

    @Test
    void presetAndRuntimeDirectoriesAreRepoRelative() {
        assertEquals(Paths.get("scenarios"), ScenarioIO.getPresetDir());
        assertEquals(Paths.get("config", "bettereve", "scenarios"), ScenarioIO.getScenarioDir());
    }

    @Test
    void listsPresetScenariosSortedWithoutExtension() {
        List<String> names = new JsonScenarioStorage().listScenarios();

        assertTrue(names.containsAll(List.of("default", "red_blue_versus", "simple_red_blue")),
                "缺少仓库内预设剧本: " + names);
        for (String name : names) {
            assertFalse(name.endsWith(".json"), "剧本名不应带扩展名: " + name);
        }
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        assertEquals(sorted, names);
    }

    @Test
    void missingScenarioThrowsLoadException() {
        ScenarioLoadException error = assertThrows(ScenarioLoadException.class,
                () -> new JsonScenarioStorage().readScenario("__missing_scenario__"));
        assertTrue(error.getMessage().contains("__missing_scenario__"), error.getMessage());
    }
}
