import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "dev.sshtunnelexporter"
version = "0.3.0" // x-release-please-version

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        create(IntelliJPlatformType.DataGrip, "2026.1.3")
        bundledPlugin("com.intellij.database")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "231"
            untilBuild = provider { null } // open-ended: compatible with all newer builds
        }
    }
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.DataGrip, "2023.1.2") // floor (sinceBuild 231) — verified Compatible
            select {                                       // newest DataGrip release(s), resolved dynamically each run
                types = listOf(IntelliJPlatformType.DataGrip)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "261" // current major and up → tracks latest without pinning the patch
            }
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // channels = listOf("default")  // use "eap"/"beta" for pre-release channels
    }
}

kotlin { jvmToolchain(21) }
