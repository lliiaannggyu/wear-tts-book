# ============================================================
#  R8 / ProGuard 规则 —— TTS Book (Wear OS)
#  用途：开启 R8 后，防止 Gson 反射、Kotlin、Compose 相关代码被误删
# ============================================================

# ===== 基础属性：保留注解 / 泛型 / 内部类 / 行号 =====
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== Android 四大组件（Manifest 中声明的）=====
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ===== 自定义 View 的构造器（XML 中引用时反射需要）=====
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== 保留 Parcelable 实现（如果有跨进程传递）=====
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ===== 保留 Serializable 的字段（Gson 有时会用到）=====
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
#  Gson 相关（本项目重点）
#  Gson 依赖反射读写字段，必须保留字段名与无参构造器
# ============================================================

# 保留 Gson 库本体
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }

# 保留 Gson 的序列化/反序列化扩展点
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class * implements com.google.gson.InstanceCreator
-keep class * implements com.google.gson.TypeAdapter

# 保留项目内所有类的字段名（Book / Chapter / Bookmark 等被 Gson 序列化）
-keepclassmembers class com.tengwear.ttsbookm3e.** {
    <fields>;
}

# 保留项目内所有类的无参构造器（Gson 反序列化时反射创建对象）
-keepclassmembers class com.tengwear.ttsbookm3e.** {
    <init>();
}

# ============================================================
#  Kotlin 相关
# ============================================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin 协程
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }

# Kotlin 反射（如果有用到）
-dontwarn kotlin.reflect.**
-keep class kotlin.reflect.** { *; }

# ============================================================
#  Jetpack Compose 相关
# ============================================================
-dontwarn androidx.compose.**
-dontwarn androidx.compose.runtime.**
-dontwarn androidx.compose.ui.**

# Compose 编译器生成的 Composer 相关（一般不需要额外规则，AGP 已内置）

# ============================================================
#  Wear OS Compose 相关
# ============================================================
-dontwarn androidx.wear.**
-dontwarn androidx.wear.compose.**

# ============================================================
#  AndroidX 通用
# ============================================================
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

-keep class androidx.core.** { *; }
-dontwarn androidx.core.**

-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# ============================================================
#  通用警告抑制
# ============================================================
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================================
#  保留调试信息（崩溃时便于定位，release 上线后不影响性能）
# ============================================================
-keepattributes LineNumberTable,SourceFile