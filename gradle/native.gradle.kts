val buildNative = tasks.register<Exec>("buildNative") {
    inputs.dir("native/windows/src")
    inputs.dir("native/windows/vendor")
    inputs.file("native/windows/build.ps1")
    inputs.file("version.properties")
    outputs.file("native/windows/build/file-lock-inspector-helper.exe")
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "native/windows/build.ps1")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(buildNative)
    from("native/windows/build/file-lock-inspector-helper.exe") { into("native/windows-x64") }
    from("LICENSE") { into("META-INF") }
    from("third-party-notices") { into("META-INF/third-party-notices") }
}
