package com.example.bettereve.render;

import net.minecraft.client.Minecraft;

/**
 * 简单工具类，用于在运行时获取 Minecraft 1.20.1 版本的 Options 类信息
 */
public class OptionsInspector {
    public static void inspect() {
        Minecraft mc = Minecraft.getInstance();
        System.out.println("=== Options 类信息 ===");
        System.out.println("Options 实例: " + mc.options);
        System.out.println("Options 类: " + mc.options.getClass().getName());
        System.out.println("=== 字段列表 ===");
        
        for (java.lang.reflect.Field field : mc.options.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            System.out.println("- " + field.getName() + " : " + field.getType().getName());
        }
        
        System.out.println("=== 方法列表 ===");
        for (java.lang.reflect.Method method : mc.options.getClass().getDeclaredMethods()) {
            System.out.println("- " + method.getName() + " : " + method.getReturnType().getName());
        }
        
        System.out.println("=== 查找包含 \"outline\" 或 \"entity\" 的字段 ===");
        for (java.lang.reflect.Field field : mc.options.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getName().toLowerCase().contains("outline") || field.getName().toLowerCase().contains("entity")) {
                System.out.println("- " + field.getName() + " : " + field.getType().getName());
            }
        }
    }
}