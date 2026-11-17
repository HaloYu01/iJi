import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.0+ 已内置 Kotlin 支持，org.jetbrains.kotlin.android 插件自 9.0 起冗余且会直接报错，移除。
    // Compose 编译器仍由 kotlin.compose 插件提供。
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ── 正式签名 ──
// release.jks / keystore.properties 都不进版本库，只在本机（和密钥保管处）存在。
// 缺文件时 release 仍可构建为未签名包，避免别的机器 clone 后连编译都过不去；
// 分发前务必确认产物已签名（apksigner verify）。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val releaseKeystore = keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it) }
val hasReleaseSigning = releaseKeystore?.isFile == true &&
    listOf("storePassword", "keyAlias", "keyPassword").all {
        !keystoreProperties.getProperty(it).isNullOrBlank()
    }

android {
    namespace = "com.iji.app"
    // miuix 0.9.3 要求 compileSdk 37+（AGP 8.7.3 最高推荐 35，已在 gradle.properties 中显式抑制该告警）
    compileSdk = 37
    // 钉在 35：36.0.0 的 aidl 会把调用命令行（含中文用户名路径的 GBK 字节）写进生成文件头，
    // javac 按 UTF-8 读直接报「不可映射字符」——2026-10-30 实测
    buildToolsVersion = "35.0.0"

    defaultConfig {
        // 应用身份，2026-11-13 由 com.iji.app 迁来；namespace（代码包）不变
        applicationId = "com.haloyu.iji"
        // 2026-11-13 从 33 降为 30，兼容 Android 11+
        minSdk = 30
        // 36 = Live Updates（原生实况通知）最低 targetSdk；36 的行为变化只有预测性返回默认开启
        targetSdk = 36
        // 版本口径：versionName 与 AboutData.changelogData 的第一条必须对应；
        // 发布日期只记在更新日志里，页面通过 changelogOf(versionName) 取，不另设常量。
        versionCode = 5
        versionName = "1.0.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    // ABI 分包只给 release：ML Kit OCR 的 .so 有 4 个 ABI 共 39 MB，真机只加载当前 ABI，
    // 打进同一个 universal 包纯属浪费。debug 不分包，保持单一 app-debug.apk 的既有交付路径。
    // 仅在显式构建 release 时启用，避免 IDE sync / assembleDebug 改变产物形态。
    val releaseRequested = gradle.startParameter.taskNames.any {
        it.contains("Release", ignoreCase = true)
    }
    splits {
        abi {
            isEnable = releaseRequested
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        // Shizuku 的 shell 通道是 UserService：需要 AIDL 生成 IShizukuShell.Stub
        aidl = true
        // 保留 BuildConfig 生成（调试判断与部分依赖仍会读 BuildConfig.DEBUG）
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // MIUI 组件库图标（小米 HyperOS 风格）
    implementation(libs.miuix.icons)
    // Miuix 核心组件库（MiuixButton / MiuixCard / MiuixTopAppBar / MiuixSwitch 等）
    implementation(libs.miuix.ui)
    // Miuix squircle 平滑圆角
    implementation(libs.miuix.squircle)
    // Miuix 弹层（OverlayDialog / OverlayBottomSheet）内部使用 NavigationBackHandler，
    // 必须在 Activity 组合根提供 LocalNavigationEventDispatcher（见 MainActivity），
    // 否则弹层显示时崩溃。版本与 Miuix 0.9.3 解析结果保持一致（1.1.2）。
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

    // Room 本地持久化
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 安全存储：应用锁 PIN 经 Android Keystore 加密落盘（EncryptedSharedPreferences）
    implementation(libs.androidx.security.crypto)

    // 图片异步加载（头像 URI 解码 + 采样 + LRU 缓存，替换手写 BitmapFactory）
    implementation(libs.coil.compose)

    // 偏好设置统一存储（Preferences DataStore，替代散落的 SharedPreferences 直连）
    implementation(libs.androidx.datastore.preferences)

    // 后台任务：预算每日巡检（BudgetCheckWorker）
    implementation(libs.androidx.work.runtime.ktx)

    // 小票 OCR 本地兜底：未配置 API Key / 离线时仍能识别小票，且隐私更好（图片不出设备）
    implementation(libs.mlkit.text.recognition.chinese)

    // Shizuku：用户授权后以 shell 身份执行 screencap —— 无需无障碍服务、无 MediaProjection 弹窗。
    // 只用公开 API（bindUserService + AIDL），不反射 Shizuku 的私有 newProcess。
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.api.bypass)

    // 网络请求（AI 记账）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // 指纹解锁：使用平台 android.hardware.biometrics.BiometricPrompt（见 AppLockManager）。
    // 不要换回 androidx.biometric：它带入 fragment 1.2.5，会拦截 startActivityForResult
    // 并对 activity 1.9.3 的 requestCode 抛 "Can only use lower 16 bits for requestCode"。

    // 液态玻璃底部导航（AGSL RuntimeShader 编译需要）
    implementation("org.jetbrains:annotations:26.1.0")

    debugImplementation(libs.androidx.ui.tooling)

    // 单元测试（P1-4：建立测试基线，防止回归）
    testImplementation(libs.junit)
}

// JDK 24 下 Gradle 测试 worker 需要放开 native-access，否则 worker 进程无法启动
tasks.withType<org.gradle.api.tasks.testing.Test> {
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        // JDK 21+ 模块边界：放开 worker 引导所需的反射
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
}

// ── aidl 生成文件编码修复 ──
// build-tools 36 的 aidl 会把调用命令行原样写进生成文件头注释；命令行里含中文
// 用户名路径（本机是 GBK 字节），javac 按 UTF-8 读取直接报「不可映射字符」。
// AGP 9.3 实测不按 buildToolsVersion 选 aidl，所以在生成后、javac 编译前
// 统一把 aidl 产物重编码为 UTF-8——工具链怎么换版本都不受影响。
val fixAidlEncoding by tasks.registering {
    doLast {
        val root = layout.buildDirectory.dir("generated/aidl_source_output_dir").get().asFile
        if (!root.exists()) return@doLast
        root.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { file ->
            // 防御：正常生成文件 ≤ 几十 KB；出现巨型文件必是 aidl 生成失控
            //（2026-11-04 实测 release 变体写出过 1.08GB），直接删除待重新生成
            if (file.length() > 5_000_000L) {
                file.delete()
                return@forEach
            }
            val bytes = file.readBytes()
            // UTF-8 解码出现 U+FFFD 即说明文件不是合法 UTF-8（被工具按本地码页写出）
            if ('\uFFFD' in String(bytes, Charsets.UTF_8)) {
                file.writeText(String(bytes, charset("GBK")))
            }
        }
    }
}
afterEvaluate {
    tasks.matching { it.name.endsWith("JavaWithJavac") }.configureEach {
        dependsOn(fixAidlEncoding)
    }
    // 保证顺序：aidl 生成 → 编码修复 → javac
    tasks.matching { it.name.endsWith("Aidl") }.configureEach {
        fixAidlEncoding.get().mustRunAfter(this)
    }
}
