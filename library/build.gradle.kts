// 全部依赖
dependencies {
    // 导出子模块
    api(project(":common"))       // 日志器和临时成员注解
    api(project(":drivers"))      // 传感器驱动
    api(project(":locator"))      // 定位融合
    api(project(":planner"))      // 规划器
    api(project(":pathfollower")) // 循线控制算法
    api(project(":painter"))      // 调试与绘图功能
    // 导出外部依赖
    api(fileTree("../libs"))
    api("net.java.dev.jna", "jna", "+")
    api("com.fazecast", "jSerialComm", "+")
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
        manifest { attributes("Main-Class" to "cn.autolabor.baafs.MainKt") }
        group = JavaBasePlugin.BUILD_TASK_NAME
        description = "pack jar to run program directly"
        archiveClassifier.set(name)
        from(sourceSets.main.get().output,
             configurations.runtimeClasspath.get()
                 .map { if (it.isDirectory) it else zipTree(it) })
    }
}


