package com.sanzineo.bettereve.faction;

import com.google.common.base.Strings;

public class Faction {
    private final String id;
    private final String name;
    private final String color;
    private final boolean enabled;

    public Faction(String id, String name, String color, boolean enabled) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getColorInt() {
        if (Strings.isNullOrEmpty(color) || !color.startsWith("#")) {
            return 0xFFFFFF; // 默认白色
        }
        try {
            return Integer.parseInt(color.substring(1), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF; // 解析失败时返回白色
        }
    }

    // 获取红色分量 (0-255)
    public int getRed() {
        return (getColorInt() >> 16) & 0xFF;
    }

    // 获取绿色分量 (0-255)
    public int getGreen() {
        return (getColorInt() >> 8) & 0xFF;
    }

    // 获取蓝色分量 (0-255)
    public int getBlue() {
        return getColorInt() & 0xFF;
    }
}
