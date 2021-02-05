import org.gradle.api.file.DuplicatesStrategy.INCLUDE

dependencies {
    implementation("com.fazecast", "jSerialComm", "+")
    implementation("net.java.dev.jna", "jna", "+")
    implementation(project(":common"))
    testImplementation(project(":painter")) // 调试与绘图功能
}

"fatjar".let { name ->
    tasks["build"].dependsOn(name)
    // 打包任务
    tasks.register<Jar>(name) {
        group = JavaBasePlugin.BUILD_TASK_NAME
        description = "library for drivers only"
        archiveClassifier.set(name)
        duplicatesStrategy = INCLUDE
        from(sourceSets.main.get().output,
             configurations.runtimeClasspath.get()
                 .map { if (it.isDirectory) it else zipTree(it) })
    }
}
