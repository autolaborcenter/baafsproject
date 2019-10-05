// 全部依赖
dependencies {
    // 导出子模块
    api(project(":common"))           // 日志器和临时成员注解
    api(project(":drivers"))          // 传感器驱动
    api(project(":locator"))          // 定位融合
    api(project(":obstacledetector")) // 障碍物检测与规避
    api(project(":pathfollower"))     // 循线控制算法
    api(project(":painter"))          // 调试与绘图功能
    // 导出外部依赖
    api(fileTree("../libs"))
    api("org.mechdancer", "linearalgebra", "+")
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "+")
    api("net.java.dev.jna", "jna", "+")
    api("com.fazecast", "jSerialComm", "+")
    // 其他测试
    testImplementation(fileTree("../libs-test"))
    testImplementation("com.google.protobuf", "protobuf-java", "2.6.1")
    testImplementation("org.zeromq", "jeromq", "0.5.1")
}

"copyJar".let { name ->
    tasks["build"].dependsOn(name)
    tasks.register<Copy>(name) {
        group = JavaBasePlugin.BUILD_TASK_NAME
        description = "copy jar to root libs"
        from("$buildDir/libs")
        include("*-direct-application.jar")
        into("${rootProject.buildDir}/libs")
    }
    tasks[name].dependsOn("direct-application")
}

"direct-application".let { name ->
    // 打包任务
    tasks["copyJar"].dependsOn(name)
    tasks.register<Jar>(name) {
        manifest { attributes("Main-Class" to "org.mechdancer.baafs.modules.MainKt") }
        group = JavaBasePlugin.BUILD_TASK_NAME
        description = "pack jar to run program directly"
        archiveClassifier.set(name)
        from(sourceSets.main.get().output,
             configurations.runtimeClasspath.get()
                 .map { if (it.isDirectory) it else zipTree(it) })
    }
}


