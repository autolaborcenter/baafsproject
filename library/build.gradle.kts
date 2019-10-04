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

with("fatJar") {
    // 打包任务
    tasks["build"].dependsOn(this)
    tasks.register<Jar>(this) {
        group = JavaBasePlugin.BUILD_TASK_NAME
        description = "Packs binary output with dependencies"
        archiveClassifier.set("all-in-one")
        from(sourceSets.main.get()
                 .output
                 .filter { "resources" !in it.path },
             configurations.runtimeClasspath.get()
                 .map { if (it.isDirectory) it else zipTree(it) })
    }
}
