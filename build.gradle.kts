import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    `build-scan`
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
//    publishAlways()
}

// 包括主项目的构建脚本
allprojects {
    apply(plugin = "kotlin")
    group = "cn.autolabor"
    version = "v0.0.9"
    repositories {
        mavenCentral()
        jcenter()
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions { jvmTarget = "1.8" }
    }
    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    dependencies {
        // 自动依赖 kotlin 标准库
        implementation(kotlin("stdlib-jdk8"))
        // 单元测试
        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
    }
    // 源码导出任务
    with("sourcesJar") {
        tasks["jar"].dependsOn(this)
        task<Jar>(this) {
            archiveClassifier.set("sources")
            group = "build"

            from(sourceSets["main"].allSource)
        }
    }
}

// 排除主项目的构建脚本
subprojects {
    dependencies {
        // 子项目自动依赖重要数学和定义库
        implementation("org.mechdancer", "linearalgebra", "+")
        implementation(files("../libs/simulator-0.0.1.jar"))
    }
}

// 主项目依赖项
dependencies {
    // 导出 kotlin 标准库
    implementation(kotlin("script-runtime"))
    // 导出子模块
    implementation(project(":common"))           // 日志器和临时成员注解
    implementation(project(":drivers"))          // 传感器驱动
    implementation(project(":locator"))          // 定位融合
    implementation(project(":obstacledetector")) // 障碍物检测与规避
    implementation(project(":pathfollower"))     // 循线控制算法
    implementation(project(":painter"))          // 调试与绘图功能
    // 导出外部依赖
    implementation(fileTree("libs"))
    implementation("org.mechdancer", "linearalgebra", "+")
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "+")
    implementation("net.java.dev.jna", "jna", "+")
    implementation("com.fazecast", "jSerialComm", "+")
    // 其他测试
    testImplementation(fileTree("libs-test"))
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
        println("all classes:")
        from(sourceSets
                 .main
                 .get()
                 .output
                 .filter { "resources" !in it.path }
                 .onEach { println(it.path) },
             configurations
                 .runtimeClasspath
                 .get()
                 .filterNot { it.name.startsWith("kotlin-") }
                 .distinct()
                 .onEach { println(it.name) }
                 .map { if (it.isDirectory) it else zipTree(it) })
        println()
    }
}

