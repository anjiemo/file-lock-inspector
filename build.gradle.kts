import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "io.github.anjiemo"
val versionProperties = Properties().apply {
    file("version.properties").inputStream().use { load(it) }
}
version = versionProperties.getProperty("version")

repositories {
    providers.gradleProperty("mavenMirror").orNull?.let { maven(it) }
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

val renderDocumentation by tasks.registering {
    val configuredVersion = version.toString()
    val sourceFiles = fileTree(".") {
        include("README.md", "README.zh-CN.md", "docs/**", "LICENSE", "third-party-notices/**", "version.properties")
        exclude("build/**")
    }
    inputs.files(sourceFiles, "version.properties")
    outputs.dir(layout.buildDirectory.dir("rendered-docs"))
    doLast {
        val destination = layout.buildDirectory.dir("rendered-docs").get().asFile
        delete(destination)
        sourceFiles.forEach { source ->
            val target = destination.resolve(source.relativeTo(projectDir).path)
            target.parentFile.mkdirs()
            val bytes = source.readBytes()
            target.writeBytes(if (source.extension == "md") bytes.toString(Charsets.UTF_8)
                .replace("{{VERSION}}", configuredVersion).toByteArray(Charsets.UTF_8) else bytes)
        }
    }
}

val documentationZip by tasks.registering(Zip::class) {
    dependsOn(renderDocumentation)
    from(layout.buildDirectory.dir("rendered-docs"))
    archiveBaseName.set("file-lock-inspector-docs")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
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

val buildNative by tasks.registering(Exec::class) {
    inputs.dir("native/windows/src")
    inputs.dir("native/windows/vendor")
    inputs.file("native/windows/build.ps1")
    inputs.file("version.properties")
    outputs.file("native/windows/build/file-lock-inspector-helper.exe")
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "native/windows/build.ps1")
}
tasks.processResources {
    inputs.property("ideSmoke", providers.gradleProperty("ideSmoke").isPresent)
    dependsOn(buildNative)
    from("native/windows/build/file-lock-inspector-helper.exe") { into("native/windows-x64") }
    from("LICENSE") { into("META-INF") }
    from("third-party-notices") { into("META-INF/third-party-notices") }
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
    dependsOn(documentationZip)
    doFirst { check(!providers.gradleProperty("ideSmoke").isPresent) { "Do not package smoke-test entry points. Omit -PideSmoke." } }
}
intellijPlatform {
    pluginConfiguration {
        id = "io.github.anjiemo.filelockinspector"
        name = "File Lock Inspector"
        ideaVersion { sinceBuild = "261"; untilBuild = "262.*" }
    }
    buildSearchableOptions = false
}
tasks.runIde {
    if (providers.gradleProperty("ideSmoke").isPresent) {
        args("file-lock-inspector-smoke", layout.buildDirectory.dir("ide-smoke").get().asFile.absolutePath)
        jvmArgs("-Djb.consents.confirmation.enabled=false", "-Dide.show.tips.on.startup.default.value=false")
    }
}
