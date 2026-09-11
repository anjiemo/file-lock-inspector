val renderDocumentation = tasks.register("renderDocumentation") {
    val configuredVersion = project.version.toString()
    val sourceFiles = fileTree(projectDir) {
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

tasks.register<Zip>("documentationZip") {
    dependsOn(renderDocumentation)
    from(layout.buildDirectory.dir("rendered-docs"))
    archiveBaseName.set("${project.name}-docs")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
