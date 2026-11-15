package com.sanzineo.bettereve;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * 测试类，用于获取 Minecraft 1.20.1 版本 Options 类的字段和方法信息
 */
public class OptionsInspectorTest {
    public static void main(String[] args) {
        // 初始化 Minecraft 客户端（需要在 Minecraft 运行时环境中执行）
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                System.out.println("Minecraft 实例为空，请在游戏运行时执行该测试");
                return;
            }
            
            Options options = mc.options;
            System.out.println("=== Options 类信息 ===");
            System.out.println("Options 实例: " + options);
            System.out.println("Options 类: " + options.getClass().getName());
            System.out.println("=== 字段列表 ===");
            
            for (java.lang.reflect.Field field : options.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                System.out.println("- " + field.getName() + " : " + field.getType().getName());
            }
            
            System.out.println("\n=== 方法列表 ===");
            for (java.lang.reflect.Method method : options.getClass().getDeclaredMethods()) {
                method.setAccessible(true);
                System.out.println("- " + method.getName() + " : " + method.getReturnType().getName());
            }
            
            System.out.println("\n=== 查找包含 \"outline\" 或 \"entity\" 的字段 ===");
            for (java.lang.reflect.Field field : options.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getName().toLowerCase().contains("outline") || field.getName().toLowerCase().contains("entity")) {
                    System.out.println("- " + field.getName() + " : " + field.getType().getName());
                }
            }
            
            System.out.println("\n=== 查找包含 \"outline\" 或 \"entity\" 的方法 ===");
            for (java.lang.reflect.Method method : options.getClass().getDeclaredMethods()) {
                method.setAccessible(true);
                if (method.getName().toLowerCase().contains("outline") || method.getName().toLowerCase().contains("entity")) {
                    System.out.println("- " + method.getName() + " : " + method.getReturnType().getName());
                }
            }
            
        } catch (Exception e) {
            System.err.println("获取 Options 类信息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}