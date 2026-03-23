package com.example.bettereve;

/**
 * 调试配置类 - 用于完全消除调试日志的性能影响
 * 
 * 这个类提供了编译时常量来控制是否启用调试输出。
 * 当DEBUG_ENABLED为false时，所有调试相关的代码都会被编译器优化掉，
 * 实现零运行时开销。
 * 
 * 使用方式：
 * 1. 在需要调试输出的地方使用if (DebugConfig.DEBUG_ENABLED)检查
 * 2. 将调试代码放在if语句块内
 * 3. 编译时如果DEBUG_ENABLED=false，这些代码会被完全移除
 */
public class DebugConfig {
    /**
     * 是否启用调试输出
     * 
     * 设置为false时：
     * - 所有调试日志代码会被编译器优化掉
     * - 零运行时开销
     * - 完全不影响游戏性能
     * 
     * 设置为true时：
     * - 启用调试输出
     * - 用于开发和测试
     * 
     * 注意：修改此值后需要重新编译项目
     */
    public static final boolean DEBUG_ENABLED = false;
    
    /**
     * 是否启用跟踪级别输出（比DEBUG更详细）
     * 仅在DEBUG_ENABLED为true时有效
     */
    public static final boolean TRACE_ENABLED = false;
    
    /**
     * 是否启用性能关键区域的调试
     * 即使DEBUG_ENABLED为true，也可以单独关闭高频区域的调试
     */
    public static final boolean PERFORMANCE_CRITICAL_DEBUG = false;
    
    /**
     * 检查是否应该输出调试日志
     * 使用此方法可以确保字符串构建等开销在调试关闭时不会发生
     */
    public static boolean isDebugEnabled() {
        return DEBUG_ENABLED;
    }
    
    /**
     * 检查是否应该输出跟踪日志
     */
    public static boolean isTraceEnabled() {
        return DEBUG_ENABLED && TRACE_ENABLED;
    }
    
    /**
     * 检查是否应该在性能关键区域输出调试
     */
    public static boolean isPerformanceCriticalDebugEnabled() {
        return DEBUG_ENABLED && PERFORMANCE_CRITICAL_DEBUG;
    }
    
    /**
     * 安全地执行调试代码
     * 使用lambda避免不必要的对象创建
     */
    public static void runIfDebug(Runnable debugCode) {
        if (DEBUG_ENABLED) {
            debugCode.run();
        }
    }
    
    /**
     * 安全地执行跟踪代码
     */
    public static void runIfTrace(Runnable traceCode) {
        if (isTraceEnabled()) {
            traceCode.run();
        }
    }
    
    /**
     * 获取当前调试配置信息（用于日志输出）
     */
    public static String getConfigInfo() {
        return String.format("DebugConfig: DEBUG_ENABLED=%s, TRACE_ENABLED=%s, PERFORMANCE_CRITICAL_DEBUG=%s",
                DEBUG_ENABLED, TRACE_ENABLED, PERFORMANCE_CRITICAL_DEBUG);
    }
}