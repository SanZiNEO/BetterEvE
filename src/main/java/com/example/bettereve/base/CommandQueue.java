package com.example.bettereve.base;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 指令队列系统
 * 用于在基地死亡后执行一系列游戏指令
 * 支持定时执行、重复执行和错误处理
 */
public class CommandQueue {
    private final List<CommandTask> commandQueue = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Entity sourceEntity;
    private final ServerLevel level;
    
    /**
     * 创建指令队列
     * @param sourceEntity 命令来源实体（通常是死亡的基地）
     * @param level 服务器等级
     */
    public CommandQueue(Entity sourceEntity, ServerLevel level) {
        this.sourceEntity = sourceEntity;
        this.level = level;
    }
    
    /**
     * 添加单个命令到队列
     * @param command 要执行的命令（如 "/kill @e[type=!minecraft:player]"）
     * @param delayMillis 延迟执行时间（毫秒）
     */
    public void addCommand(String command, long delayMillis) {
        commandQueue.add(new CommandTask(command, delayMillis));
    }
    
    /**
     * 添加多次重复命令到队列
     * @param command 要执行的命令
     * @param times 重复次数
     * @param intervalMillis 执行间隔（毫秒）
     */
    public void addRepeatedCommand(String command, int times, long intervalMillis) {
        for (int i = 0; i < times; i++) {
            addCommand(command, i * intervalMillis);
        }
    }
    
    /**
     * 执行队列中的所有命令
     */
    public void executeAll() {
        System.out.println("开始执行指令队列，共 " + commandQueue.size() + " 个命令");
        
        for (CommandTask task : commandQueue) {
            scheduler.schedule(() -> {
                try {
                    executeCommand(task.command);
                    System.out.println("成功执行命令: " + task.command);
                } catch (Exception e) {
                    System.err.println("执行命令失败: " + task.command + " - 错误: " + e.getMessage());
                    e.printStackTrace();
                }
            }, task.delayMillis, TimeUnit.MILLISECONDS);
        }
        
        // 所有命令执行完成后关闭调度器
        long maxDelay = commandQueue.stream()
            .mapToLong(t -> t.delayMillis)
            .max()
            .orElse(0);
        
        scheduler.schedule(() -> {
            scheduler.shutdown();
            System.out.println("指令队列执行完成");
        }, maxDelay + 1000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 执行单个命令
     * @param command 要执行的命令
     */
    private void executeCommand(String command) {
        // 创建命令源栈，权限等级为4（游戏管理员）
        CommandSourceStack source = new CommandSourceStack(
            sourceEntity,  // 来源实体
            new Vec3(sourceEntity.getX(), sourceEntity.getY(), sourceEntity.getZ()),  // 位置
            sourceEntity.getRotationVector(),  // 方向
            level,  // 世界
            4,  // 权限等级（游戏管理员）
            sourceEntity.getName().getString(),  // 名称
            sourceEntity.getDisplayName(),  // 显示名称
            level.getServer(),  // 服务器
            null  // 执行来源实体
        );
        
        // 执行命令
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }
    
    /**
     * 内部任务类，存储命令和延迟时间
     */
    private static class CommandTask {
        final String command;
        final long delayMillis;
        
        CommandTask(String command, long delayMillis) {
            this.command = command;
            this.delayMillis = delayMillis;
        }
    }
}