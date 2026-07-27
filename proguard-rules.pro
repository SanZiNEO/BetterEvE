# ProGuard 配置文件
# 用于 BetterEvE 模组的代码混淆和优化

# 基础配置
# 避免某些优化导致的类层次结构问题
-dontoptimize
-dontshrink
# 基础配置
-optimizationpasses 5
-dump class_files.txt
-printseeds seeds.txt
-printusage unused.txt
-printmapping mapping.txt

# 保留基本属性
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 删除注释和调试信息
-dontnote
-dontwarn
-dontobfuscate

# 保留所有公共类和方法（用于 Mod 入口）
-keep public class * {
    public protected *;
}

# 保留 BetterEvE 模组的核心类不被混淆
-keep class com.example.bettereve.Bettereve { *; }
-keep class com.example.bettereve.config.** { *; }
-keep class com.example.bettereve.network.** { *; }
-keep class com.example.bettereve.mixin.** { *; }
-keep class com.example.bettereve.level.** { *; }
-keep class com.example.bettereve.faction.** { *; }
-keep class com.example.bettereve.render.** { *; }
-keep class com.example.bettereve.counter.** { *; }
-keep class com.example.bettereve.scoreboard.** { *; }
-keep class com.example.bettereve.targeting.** { *; }
-keep class com.example.bettereve.timer.** { *; }
-keep class com.example.bettereve.ai.** { *; }
-keep class com.example.bettereve.base.** { *; }
-keep class com.example.bettereve.commands.** { *; }

# 保留 Mixin 相关类
-keep class * implements org.spongepowered.asm.mixin.extensibility.IMixinInfo { *; }

# 保留网络数据包类不被混淆
-keep class com.example.bettereve.network.** {
    public *;
    protected *;
    private *;
}

# 保留配置类不被混淆
-keep class com.example.bettereve.config.BetterEvEConfig {
    *;
}

# 保留 Forge 事件监听器类
-keepclassmembers class * {
    @net.minecraftforge.eventbus.api.SubscribeEvent *;
    @net.minecraftforge.fml.event.lifecycle.* *;
}

# 保留模组入口点和初始化方法
-keepclassmembers class com.example.bettereve.Bettereve {
    public static void *();
}

# 保留 Mixin 注解和方法
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.spongepowered.asm.mixin.* <fields>;
    @org.spongepowered.asm.mixin.* <methods>;
}

# 删除调试信息
-assumenosideeffects class java.lang.String {
    public static java.lang.String valueOf(long);
    public static java.lang.String valueOf(double);
}

-assumenosideeffects class java.io.PrintStream {
    public void println();
    public void println(int);
    public void println(long);
    public void println(float);
    public void println(double);
    public void println(boolean);
    public void println(char);
    public void println(java.lang.String);
    public void println(java.lang.Object);
}

# 删除日志信息
-assumenosideeffects class org.apache.logging.log4j.Logger {
    public void debug(...);
    public void trace(...);
}

# 保留必要的 Minecraft 和 Forge 类
-keep class net.minecraftforge.** { *; }
-keep class net.minecraft.** { *; }
-keep class com.mojang.** { *; }

# 保留资源加载相关类
-keep class net.minecraftforge.fml.** { *; }
-keep class net.minecraftforge.eventbus.** { *; }

# 保留网络通信类
-keep class net.minecraft.network.** { *; }