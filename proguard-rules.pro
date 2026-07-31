# BetterEvE mod ProGuard obfuscation config (release build)
# Strategy: obfuscate only, no shrink, no optimize (max stability).
# Keep only what the framework/reflection/config loads by name.

-dontshrink
-dontoptimize
-allowaccessmodification
-useuniqueclassmembernames
-adaptclassstrings
-overloadaggressively

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod
-renamesourcefileattribute Source

# Forge mod entrypoint and event subscriber classes/methods (event bus reflection)
-keep @net.minecraftforge.fml.common.Mod class *
-keep @net.minecraftforge.fml.common.Mod$EventBusSubscriber class *
-keepclassmembers class * {
    @net.minecraftforge.eventbus.api.SubscribeEvent *;
}
-keep class com.example.bettereve.Bettereve { *; }

# Mixin classes (bettereve.mixins.json references by name)
-keep class com.example.bettereve.mixin.** { *; }

# Scenario data model (Gson deserializes by field name)
-keep class com.example.bettereve.scenario.** { *; }

# Enums (switch mapping / data sync)
-keep enum com.example.bettereve.** { *; }

-dontwarn
-dontnote
-ignorewarnings
