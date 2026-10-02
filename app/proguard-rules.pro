# 保持 moe.shizuku 与 rikka 包名，避免 Shizuku 内部类被混淆导致运行时崩溃
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.manager.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Shizuku 通过反射调用隐藏 API，保留其签名
-keepclassmembers class * {
    @rikka.shizuku.* <methods>;
}

# 保留注解与泛型签名，便于 Shizuku AIDL 反射工作
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
