import java.util.Properties
import groovy.json.JsonOutput

plugins {
    alias(libs.plugins.androidApplication)
}

// Only use values issued for Qingke's actual package/signature; empty builds retain Android notifications.
val qingkeHyperOsBusiness = providers.gradleProperty("qingkeHyperOsBusiness").orElse("").get().trim()
val qingkeOppoConfig = linkedMapOf(
    "IntentName" to providers.gradleProperty("qingkeOppoIntentName").orElse("").get().trim(),
    "IntentVersion" to providers.gradleProperty("qingkeOppoIntentVersion").orElse("1.0.0").get().trim(),
    "IntentDescription" to providers.gradleProperty("qingkeOppoIntentDescription").orElse("青课课程实时进度").get().trim(),
    "ServiceIds" to providers.gradleProperty("qingkeOppoServiceIds").orElse("").get().trim(),
    "MilestoneUpcoming" to providers.gradleProperty("qingkeOppoMilestoneUpcoming").orElse("-1").get().trim(),
    "MilestoneOngoing" to providers.gradleProperty("qingkeOppoMilestoneOngoing").orElse("-1").get().trim(),
    "MilestoneEnded" to providers.gradleProperty("qingkeOppoMilestoneEnded").orElse("-1").get().trim(),
)
val qingkeOppoConfigured = qingkeOppoConfig.getValue("IntentName").isNotBlank() &&
    qingkeOppoConfig.getValue("IntentVersion").isNotBlank() &&
    qingkeOppoConfig.getValue("ServiceIds").split(',', ';').any { it.isNotBlank() } &&
    listOf("MilestoneUpcoming", "MilestoneOngoing", "MilestoneEnded").all {
        qingkeOppoConfig.getValue(it).toIntOrNull()?.let { code -> code >= 0 } == true
    }

fun androidConfigString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "cn.edu.gzus.qingke"
    compileSdk = 37
    buildFeatures { resValues = true }
    defaultConfig {
        applicationId = "cn.edu.gzus.qingke"
        minSdk = 26
        targetSdk = 36
        versionCode = 90
        versionName = "1.8.4"
        resValue("string", "qingke_hyperos_business", androidConfigString(qingkeHyperOsBusiness))
        qingkeOppoConfig.forEach { (key, value) ->
            // String resources prevent numeric service IDs from being parsed as manifest integers.
            val resource = "qingke_oppo_" + key.lowercase()
            resValue("string", resource, androidConfigString(value))
            manifestPlaceholders["qingkeOppo$key"] = "@string/$resource"
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    packaging {
        // 提取 native 库再装：老设备/第三方安装器对 extractNativeLibs=false 支持参差不齐。
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "DebugProbesKt.bin",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "qingke-release.jks"))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "qingke")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
            // 三套签名都开：部分第三方安装器和安全扫描只认 v1/v3。
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
}

abstract class GenerateOppoIntentConfig : DefaultTask() {
    @get:Input abstract val registration: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val output = outputDirectory.get().file("intelligent_intent_config.json").asFile
        output.parentFile.mkdirs()
        output.writeText(registration.get() + "\n", Charsets.UTF_8)
    }
}

val generateOppoIntentConfig = tasks.register<GenerateOppoIntentConfig>("generateOppoIntentConfig") {
    outputDirectory.set(layout.buildDirectory.dir("generated/oppo-intent-assets"))
    val entries = if (qingkeOppoConfigured) listOf(mapOf(
        "intentName" to qingkeOppoConfig.getValue("IntentName"),
        "intentVersion" to qingkeOppoConfig.getValue("IntentVersion"),
        "description" to listOf(qingkeOppoConfig.getValue("IntentDescription")),
    )) else emptyList()
    registration.set(JsonOutput.toJson(mapOf("intelligentIntents" to entries)))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateOppoIntentConfig, GenerateOppoIntentConfig::outputDirectory)
}
