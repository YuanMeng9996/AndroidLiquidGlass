// ============================================================================================
// 文件：backdrop/build.gradle.kts
// 作用：Compose Multiplatform 液态玻璃核心库（:backdrop）的 Gradle 构建脚本
// 本次修复：为所有目标（含 commonMain 元数据编译）开启 Kotlin 上下文参数实验特性
//          -Xcontext-parameters，解决 LayerRecorder.kt / DrawBackdropModifier.kt /
//          LayerBackdropModifier.kt 的 UNSUPPORTED_FEATURE 与
//          UNSUPPORTED_CONTEXTUAL_DECLARATION_CALL 编译错误
// 依赖插件：AGP KMP 库插件、Kotlin Multiplatform、Compose 编译器、JetBrains Compose、vanniktech 发布插件
// ============================================================================================

// 导入 Kotlin Gradle 插件的 JVM 目标字节码版本枚举；下方 android { } 里的 jvmTarget = JvmTarget.JVM_11 需要它
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// plugins：声明本模块要应用的 Gradle 插件（顺序不影响功能，保持与上游一致）
plugins {
    // com.android.kotlin.multiplatform.library —— AGP 9 为 KMP 库提供 Android 目标的官方插件（替代旧的 com.android.library）
    alias(libs.plugins.android.multiplatform.library)
    // org.jetbrains.kotlin.multiplatform —— Kotlin 多平台插件，提供 kotlin { } DSL、各 target 与 sourceSets
    alias(libs.plugins.kotlin.multiplatform)
    // org.jetbrains.kotlin.plugin.compose —— Compose 编译器插件（Kotlin 2.x 起随 Kotlin 版本发布）
    alias(libs.plugins.kotlin.compose)
    // org.jetbrains.compose —— JetBrains Compose Multiplatform 插件，提供 compose.* 依赖别名与桌面打包能力
    alias(libs.plugins.jetbrains.compose)
    // com.vanniktech.maven.publish —— Maven Central 发布插件；版本在根 build.gradle.kts 里用 version "0.36.0" apply false 声明过
    id("com.vanniktech.maven.publish")
}

// kotlin { } —— Kotlin Multiplatform 扩展块：在这里声明目标平台、源集与编译器选项
kotlin {

    // android { } —— AGP KMP 库插件提供的 Android 目标配置块（注意：不是传统的顶层 android { }）
    android {
        // minSdk：库支持的最低 Android API 级别（21 = Android 5.0）
        minSdk = 21
        // compileSdk：编译时使用的 Android SDK 版本（36 = Android 16）；CI 里已 sdkmanager 安装 platforms;android-36
        compileSdk = 36
        // buildToolsVersion：构建工具链版本；CI 里已安装 build-tools;36.1.0，二者必须对齐否则报缺包
        buildToolsVersion = "36.1.0"
        // namespace：Android 库的包命名空间，用于生成 R 类与 AAR 元数据
        namespace = "com.kyant.backdrop"

        // compilerOptions { } —— 目标级（target level）编译器选项，仅作用于 Android 目标的各个 compilation
        compilerOptions {
            // jvmTarget：Android 目标产出的 JVM 字节码版本，固定 Java 11
            // 说明：目标级只会覆盖“同名选项”，这里只设置了 jvmTarget，
            //      因此不会清掉本文件末尾扩展级配置的 freeCompilerArgs（-Xcontext-parameters 依旧继承生效）
            jvmTarget = JvmTarget.JVM_11
        }
    }

    // applyDefaultHierarchyTemplate()：启用 KMP 默认源集层级模板（自动生成 iosMain / nativeMain 等中间源集）
    applyDefaultHierarchyTemplate()

    // jvm("desktop")：声明 JVM 桌面目标，并把目标名改为 desktop（源集随之为 desktopMain / desktopTest）
    jvm("desktop")

    // js { }：声明 Kotlin/JS 目标
    js {
        // browser()：产物运行在浏览器环境（生成 webpack 相关任务）
        browser()
    }
    // wasmJs { }：声明 Kotlin/Wasm（JS 互操作）目标
    wasmJs {
        // browser()：同上，Wasm 产物跑在浏览器
        browser()
    }

    // macosArm64()：声明 macOS Apple Silicon 原生目标
    macosArm64()
    // iosArm64("iosArm64")：声明 iOS 真机（arm64）原生目标，并显式指定目标名
    iosArm64("iosArm64")
    // iosSimulatorArm64("iosSimulatorArm64")：声明 iOS 模拟器（Apple Silicon）原生目标
    iosSimulatorArm64("iosSimulatorArm64")

    // sourceSets { } —— 配置源集：依赖声明与源集之间的 dependsOn 继承关系
    sourceSets {

        // commonMain：所有平台共享的公共源集（本次四条报错的 .kt 文件都在这里）
        val commonMain = getByName("commonMain") {
            // dependencies { }：该源集的依赖
            dependencies {
                // Compose Foundation：布局、手势、基础组件
                implementation(libs.compose.foundation)
                // Compose UI：Modifier、Modifier.Node、DrawScope 等核心 UI 能力
                implementation(libs.compose.ui)
                // Compose UI Graphics：GraphicsLayer、RenderEffect、Shader 等绘制能力（液态玻璃核心依赖）
                implementation(libs.compose.ui.graphics)
                // Kyant shapes：作者自研的连续圆角/胶囊形状库
                implementation(libs.kyant.shapes)
                // JetBrains annotations：@Nullable/@ApiStatus 等注解，供源码标注使用
                implementation("org.jetbrains:annotations:26.1.0")
            }
        }

        // skikoMain：自建中间源集，用于收纳“基于 Skiko/Skia 实现”的平台共享代码
        val skikoMain = create("skikoMain") {
            // dependsOn(commonMain)：让 skikoMain 继承 commonMain 的源码可见性与依赖
            dependsOn(commonMain)
        }

        // desktopMain：JVM 桌面源集
        val desktopMain = getByName("desktopMain") {
            // 桌面端走 Skiko 实现
            dependsOn(skikoMain)
        }

        // macosArm64Main：macOS 原生源集
        val macosArm64Main = getByName("macosArm64Main") {
            // macOS 端走 Skiko 实现
            dependsOn(skikoMain)
        }

        // iosMain：由默认层级模板生成的 iOS 共享中间源集
        val iosMain = getByName("iosMain") {
            // iOS 端走 Skiko 实现
            dependsOn(skikoMain)
        }

        // iosArm64Main：iOS 真机源集；这里只是取出引用占位，层级由模板自动挂到 iosMain
        val iosArm64Main = getByName("iosArm64Main") {
        }

        // iosSimulatorArm64Main：iOS 模拟器源集；同上，仅占位
        val iosSimulatorArm64Main = getByName("iosSimulatorArm64Main") {
        }

        // jsMain：Kotlin/JS 源集
        val jsMain = getByName("jsMain") {
            // JS 端（Canvas/Skiko）走 Skiko 实现
            dependsOn(skikoMain)
        }

        // wasmJsMain：Kotlin/Wasm 源集
        val wasmJsMain = getByName("wasmJsMain") {
            // Wasm 端走 Skiko 实现
            dependsOn(skikoMain)
        }
    }

    // ★ 新增开始 ==================================================================================
    // compilerOptions { } —— 扩展级（extension level）编译器选项
    // 关键点：扩展级是“所有 target + 所有共享源集（含 commonMain / commonTest / 元数据编译）”的默认值，
    //        因此 compileCommonMainKotlinMetadata、compileKotlinAndroid、compileKotlinDesktop、
    //        compileKotlinJs、compileKotlinWasmJs、compileKotlinIosArm64 … 全部都会带上这些参数
    compilerOptions {
        // freeCompilerArgs：透传给 Kotlin 编译器的原始参数列表（-X 实验开关只能通过它传）
        // 必须用 add()/addAll()，不能用 “=” 或 “+=”（KGP 2.x 已废弃 kotlinOptions 与直接赋值写法）
        freeCompilerArgs.add(
            // -Xcontext-parameters：开启 Kotlin “上下文参数（context parameters）”实验特性
            // 它同时解决两类报错：
            //   1) 声明处 internal/LayerRecorder.kt:10  context(node: DelegatableNode)   → UNSUPPORTED_FEATURE
            //   2) 调用处 DrawBackdropModifier.kt:290/:328、LayerBackdropModifier.kt:57 → UNSUPPORTED_CONTEXTUAL_DECLARATION_CALL
            // 备注：该特性在 Kotlin 2.4.0 起转为 Stable；届时升级 Kotlin 后可以安全删掉这一行
            "-Xcontext-parameters"
        )
    }
    // ★ 新增结束 ==================================================================================
}

// mavenPublishing { } —— vanniktech 发布插件配置：定义坐标与 POM 元数据（本次未改动）
mavenPublishing {
    // publishToMavenCentral()：目标仓库为 Maven Central（Sonatype Central Portal）
    publishToMavenCentral()
    // signAllPublications()：对所有产物做 GPG 签名（Central 强制要求）
    signAllPublications()

    // coordinates(...)：groupId、artifactId、version 三元组
    coordinates("io.github.kyant0", "backdrop", "2.0.1")

    // pom { }：生成 .pom 文件所需的项目元信息
    pom {
        // name：制品显示名
        name.set("Backdrop")
        // description：一句话描述
        description.set("Compose Multiplatform Liquid Glass effects")
        // inceptionYear：项目起始年份
        inceptionYear.set("2025")
        // url：项目主页
        url.set("https://github.com/Kyant0/AndroidLiquidGlass")

        // licenses { }：许可证声明（Central 必填项）
        licenses {
            // license { }：单条许可证
            license {
                // name：许可证名称
                name.set("The Apache License, Version 2.0")
                // url：许可证正文地址
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                // distribution：分发方式，repo 表示可从仓库获取
                distribution.set("repo")
            }
        }

        // developers { }：开发者信息（Central 必填项）
        developers {
            // developer { }：单个开发者
            developer {
                // id：开发者标识
                id.set("Kyant0")
                // name：开发者名称
                name.set("Kyant")
                // url：开发者主页
                url.set("https://github.com/Kyant0")
            }
        }

        // scm { }：源码管理信息（Central 必填项）
        scm {
            // url：仓库网页地址
            url.set("https://github.com/Kyant0/AndroidLiquidGlass")
            // connection：匿名只读克隆地址
            connection.set("scm:git:git://github.com/Kyant0/AndroidLiquidGlass.git")
            // developerConnection：开发者可写克隆地址
            developerConnection.set("scm:git:ssh://git@github.com/Kyant0/AndroidLiquidGlass.git")
        }
    }
}
