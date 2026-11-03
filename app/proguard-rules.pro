# 保留数据类
-keep class com.iji.app.data.** { *; }
-keep class com.iji.app.MainViewModel { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Miuix
-keep class top.yukonga.miuix.** { *; }
-dontwarn top.yukonga.miuix.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# AndroidX Security（EncryptedSharedPreferences / MasterKey）
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# 保留 Compose 相关
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# WorkManager：Worker 由框架反射实例化（Context + WorkerParameters 构造），
# 不保留会被 R8 移除构造函数，导致定时任务在 release 包里静默失效。
-keep class com.iji.app.budget.BudgetCheckWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.iji.app.reminder.ReminderWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.iji.app.backup.AutoBackupWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# 无障碍服务与通知动作接收器：由系统按清单/广播显式实例化，
# 保留构造与入口方法，避免被 R8 改名后系统找不到组件。
-keep class com.iji.app.accessibility.PaymentNotifyAccessibilityService { *; }
-keep class com.iji.app.accessibility.PaymentActionReceiver { *; }

# Google Tink（AppLockManager 加密）引用的 errorprone 编译期注解，运行时不需要
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
