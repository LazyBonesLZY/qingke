import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)

    // miuix-blur 的桌面 artifact 需要 Java 21 字节码。测试执行器跟随当前 JVM：
    // JDK 21+ 上跑离屏渲染测试，JDK 17（CI）跳过并保留纯逻辑回归。
    tasks.withType<Test>().configureEach {
        val current = JavaVersion.current()
        if (current >= JavaVersion.VERSION_21) {
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(current.majorVersion))
                },
            )
            systemProperty("qingke.render.tests", "true")
        }
    }

    android {
        namespace = "cn.edu.gzus.qingke.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.animation)
                implementation(libs.miuix.ui)
                implementation(libs.miuix.preference)
                implementation(libs.miuix.icons)
                implementation(libs.miuix.blur)
                implementation(libs.miuix.squircle)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content)
                implementation(libs.ktor.serialization.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.ktor.client.okhttp)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "cn.edu.gzus.qingke.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "qingke"
            packageVersion = "1.0.0"
        }
    }
}

// 渲染测试仅在 JDK 21+（本机验证）执行；CI 的 JDK 17 只跑纯逻辑回归。
tasks.named<Test>("desktopTest") {
    if (JavaVersion.current() < JavaVersion.VERSION_21) {
        exclude("**/BottomBarRenderTest.class")
    }
}
