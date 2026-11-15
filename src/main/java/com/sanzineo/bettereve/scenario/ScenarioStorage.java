package com.sanzineo.bettereve.scenario;

import java.util.List;

/**
 * 剧本存储接口 — 支持不同的存储后端（JSON 文件、加密文件、数据库等）。
 * 
 * 后续要换存储方式（如加密二进制文件）只需新建一个实现类，
 * 在 ScenarioIO 中切换即可，不影响其他代码。
 */
public interface ScenarioStorage {
    
    /**
     * 列出所有可用的剧本名称（不含扩展名）。
     */
    List<String> listScenarios();
    
    /**
     * 读取指定剧本。
     * @param name 剧本名称（不含扩展名）
     * @return 解析后的 Scenario 对象
     * @throws ScenarioLoadException 文件不存在或解析失败
     */
    Scenario readScenario(String name) throws ScenarioLoadException;
    
    /**
     * 写入剧本到存储。
     */
    void writeScenario(Scenario scenario);
    
    /**
     * 获取存储根目录的描述（仅用于日志/显示）。
     */
    default String getStorageDescription() {
        return getClass().getSimpleName();
    }
}
