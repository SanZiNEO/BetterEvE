package com.sanzineo.bettereve.scenario;

/**
 * 剧本加载异常。
 */
public class ScenarioLoadException extends Exception {
    public ScenarioLoadException(String message) {
        super(message);
    }
    public ScenarioLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
