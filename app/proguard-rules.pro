# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ======================== 调试信息 ========================
# 保留行号信息，便于崩溃日志定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ======================== Room 数据库 ========================
# 保留 Room 实体类（反射序列化）
-keep class com.wind.ggbond.classtime.data.local.entity.** { *; }
# 保留 Room DAO 接口
-keep class com.wind.ggbond.classtime.data.local.dao.** { *; }
# 保留 Room 数据库类
-keep class com.wind.ggbond.classtime.data.local.database.** { *; }
# 保留 Room TypeConverter
-keep class com.wind.ggbond.classtime.data.local.converter.** { *; }

# ======================== Gson 序列化 ========================
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 保留所有可能被Gson反序列化的类的字段名
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留数据模型类（Gson 反序列化需要字段名）
-keep class com.wind.ggbond.classtime.data.model.** { *; }
-keep class com.wind.ggbond.classtime.data.local.entity.** { *; }
-keep class com.wind.ggbond.classtime.ui.screen.scheduleimport.ParsedCourse { *; }
-keep class com.wind.ggbond.classtime.ui.screen.scheduleimport.JsonCourse { *; }
-keep class com.wind.ggbond.classtime.ui.screen.scheduleimport.SmartImportViewModel$JsonCourse { *; }

# 保留 SchoolRepository 内部的 SchoolData 类（Gson 反序列化 schools.json 需要）
-keep class com.wind.ggbond.classtime.data.repository.SchoolRepository$SchoolData { *; }
-keepclassmembers class com.wind.ggbond.classtime.data.repository.SchoolRepository$SchoolData {
    *;
}

# 保留自动更新相关数据类（Gson序列化到SharedPreferences）
-keep class com.wind.ggbond.classtime.util.AutoUpdateConfig { *; }
-keep class com.wind.ggbond.classtime.util.UpdateLogEntry { *; }
-keep class com.wind.ggbond.classtime.util.UpdateStatus { *; }

# 特别保护Course实体的所有字段，防止被混淆
-keepclassmembers class com.wind.ggbond.classtime.data.local.entity.Course {
    *;
}

# 确保Gson能够正确创建匿名内部类（TypeToken使用）
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# ======================== Hilt 依赖注入 ========================
# Hilt 自身已包含 ProGuard 规则，通常无需额外配置
# 但保留 EntryPoint 接口以防万一
-keep @dagger.hilt.EntryPoint interface * { *; }

# 保留 Hilt 生成的类
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.AndroidEntryPoint
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}

# 保留所有 Hilt_ 前缀的生成类
-keep class com.wind.ggbond.classtime.Hilt_* { *; }

# 忽略 Hilt 生成类的警告
-dontwarn com.wind.ggbond.classtime.Hilt_CourseScheduleApp
-dontwarn com.wind.ggbond.classtime.Hilt_MainActivity
-dontwarn com.wind.ggbond.classtime.receiver.Hilt_BootReceiver
-dontwarn com.wind.ggbond.classtime.ui.screen.update.Hilt_FloatingUpdateActivity

# ======================== OkHttp / Retrofit ========================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }

# ======================== WebView JS 接口 ========================
# 保留所有 Extractor 类（WebView JS 提取器，通过反射/工厂模式创建）
-keep class com.wind.ggbond.classtime.util.extractor.** { *; }

# ======================== Glance Widget ========================
# 保留桌面小组件相关类
-keep class com.wind.ggbond.classtime.widget.** { *; }

# ======================== BroadcastReceiver ========================
# 保留通过 Manifest 注册的 Receiver
-keep class com.wind.ggbond.classtime.receiver.** { *; }

# ======================== Jsoup ========================
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# ======================== Kotlin 序列化 ========================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ======================== Compose ========================
# Compose 编译器插件已自动处理，通常无需额外规则

# ======================== Google Tink 加密库 ========================
# Tink 引用了 errorprone 注解（仅编译期使用），运行时不需要
-dontwarn com.google.errorprone.annotations.**

# ======================== Hilt 生成类保护 ========================
-keep class com.wind.ggbond.classtime.CourseScheduleApp_GeneratedInjector { *; }
-keep class com.wind.ggbond.classtime.**_GeneratedInjector { *; }
-keep class com.wind.ggbond.classtime.**_HiltModules* { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ======================== 枚举方法保留 ========================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
