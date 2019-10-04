// 全部依赖
dependencies {
    // 导出子模块
    implementation(project(":common"))           // 日志器和临时成员注解
    implementation(project(":drivers"))          // 传感器驱动
    implementation(project(":locator"))          // 定位融合
    implementation(project(":obstacledetector")) // 障碍物检测与规避
    implementation(project(":pathfollower"))     // 循线控制算法
    implementation(project(":painter"))          // 调试与绘图功能
    // 导出外部依赖
    implementation(fileTree("../libs"))
    implementation("org.mechdancer", "linearalgebra", "+")
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "+")
    implementation("net.java.dev.jna", "jna", "+")
    implementation("com.fazecast", "jSerialComm", "+")
    // 其他测试
    testImplementation(fileTree("../libs-test"))
    testImplementation("com.google.protobuf", "protobuf-java", "2.6.1")
    testImplementation("org.zeromq", "jeromq", "0.5.1")
}

//tasks.withType(Jar::class) {
//    archiveClassifier.set("all-in-one")
//    println("all classes:")
//    from(sourceSets
//             .main
//             .get()
//             .output
//             .filter { "resources" !in it.path }
//             .onEach { println(it.path) },
//         configurations
//             .runtimeClasspath
//             .get()
//             .filterNot { it.name.startsWith("kotlin-") }
//             .distinct()
//             .onEach { println(it.name) }
//             .map { if (it.isDirectory) it else zipTree(it) })
//    println()
//}
