import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

fun configuration(path: String) = Properties().apply {
    providers.fileContents(layout.projectDirectory.file(path)).asText.get().reader().use { load(it) }
}
fun Properties.required(key: String): String = getProperty(key)?.takeIf { it.isNotBlank() }
    ?: error("Missing configuration property: $key")

val pluginMetadata = configuration("config/plugin.properties")
val compatibility = configuration("config/compatibility.properties")
group = pluginMetadata.required("group")
version = configuration("version.properties").required("version")

apply(from = "gradle/documentation.gradle.kts")
apply(from = "gradle/native.gradle.kts")

repositories {
    providers.gradleProperty("mavenMirror").orNull?.let { maven(it) }
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

val ideHome = providers.gradleProperty("idePath").orElse(providers.environmentVariable("IDE_HOME"))
dependencies {
    intellijPlatform {
        local(ideHome.get())
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.android")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    sourceSets.main { kotlin.srcDirs("plugin/src/main/kotlin", "android-studio/src/main/kotlin") }
    sourceSets.test { kotlin.srcDirs("plugin/src/test/kotlin") }
    if (providers.gradleProperty("ideSmoke").isPresent) sourceSets.main { kotlin.srcDir("plugin/src/smoke/kotlin") }
}
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
sourceSets.main { resources.srcDirs("plugin/src/main/resources") }

tasks.processResources {
    inputs.property("ideSmoke", providers.gradleProperty("ideSmoke").isPresent)
}
tasks.patchPluginXml {
    val smoke = providers.gradleProperty("ideSmoke").isPresent
    inputs.property("ideSmoke", smoke)
    doLast {
        if (smoke) {
            val file = outputFile.get().asFile
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val extensions = document.createElement("extensions").apply { setAttribute("defaultExtensionNs", "com.intellij") }
            extensions.appendChild(document.createElement("appStarter").apply {
                setAttribute("id", "file-lock-inspector-smoke")
                setAttribute("implementation", "io.github.anjiemo.filelockinspector.IdeSmokeStarter")
            })
            document.documentElement.appendChild(extensions)
            javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
                javax.xml.transform.dom.DOMSource(document), javax.xml.transform.stream.StreamResult(file))
        }
    }
}
tasks.test { useJUnitPlatform() }
tasks.buildPlugin {
    dependsOn("documentationZip")
    doFirst { check(!providers.gradleProperty("ideSmoke").isPresent) { "Do not package smoke-test entry points. Omit -PideSmoke." } }
}
intellijPlatform {
    pluginConfiguration {
        id = pluginMetadata.required("id")
        name = pluginMetadata.required("name")
        description = providers.fileContents(layout.projectDirectory.file("config/description.en.html")).asText.zip(
            providers.fileContents(layout.projectDirectory.file("config/description.zh-CN.html")).asText
        ) { english, chinese -> "${english.trim()}\n${chinese.trim()}" }
        vendor {
            name = pluginMetadata.required("vendor.name")
            url = pluginMetadata.required("vendor.url")
        }
        ideaVersion {
            sinceBuild = compatibility.required("sinceBuild")
            untilBuild = compatibility.required("untilBuild")
        }
    }
    buildSearchableOptions = false
}
tasks.runIde {
    if (providers.gradleProperty("ideSmoke").isPresent) {
        args("file-lock-inspector-smoke", layout.buildDirectory.dir("ide-smoke").get().asFile.absolutePath)
        jvmArgs("-Djb.consents.confirmation.enabled=false", "-Dide.show.tips.on.startup.default.value=false")
    }
}
