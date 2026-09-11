pluginManagement {
    repositories {
        providers.gradleProperty("mavenMirror").orNull?.let { maven(it) }
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "file-lock-inspector"
