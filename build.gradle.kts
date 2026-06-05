import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import java.io.File

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "dev.sshtunnelexporter"
version = "0.3.0" // x-release-please-version

/**
 * Render the plugin <change-notes> HTML from the latest CHANGELOG.md section (maintained by
 * release-please). Single source of truth — no hand-edited notes in plugin.xml. Returns null
 * when there is nothing to render, leaving plugin.xml untouched.
 */
fun renderChangeNotes(changelog: File): String? {
    if (!changelog.exists()) return null
    val lines = changelog.readLines()
    val header = lines.indexOfFirst { it.startsWith("## ") }
    if (header < 0) return null
    val after = lines.drop(header + 1)
    val next = after.indexOfFirst { it.startsWith("## ") }
    val body = if (next < 0) after else after.take(next)

    fun inline(s: String): String = s
        .replace(Regex("""\[([^]]+)]\(([^)]+)\)""")) { m -> "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>" }
        .replace(Regex("`([^`]+)`")) { m -> "<code>${m.groupValues[1]}</code>" }

    val html = StringBuilder()
    var inList = false
    fun closeList() { if (inList) { html.append("</ul>"); inList = false } }
    for (raw in body) {
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("### ") -> { closeList(); html.append("<p><b>${line.removePrefix("### ")}</b></p>") }
            line.startsWith("* ") || line.startsWith("- ") -> {
                if (!inList) { html.append("<ul>"); inList = true }
                html.append("<li>${inline(line.drop(2))}</li>")
            }
            else -> { closeList(); html.append("<p>${inline(line)}</p>") }
        }
    }
    closeList()
    return html.toString().ifBlank { null }
}

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
        changeNotes = provider { renderChangeNotes(file("CHANGELOG.md")) }
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
