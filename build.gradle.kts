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

version = "1.0-SNAPSHOT"

// 包括主项目的构建脚本
allprojects {
    apply(plugin = "kotlin")
    group = "cn.autolabor"
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
    // 源码导出任务
    val sourceTaskName = "sourcesJar"
    task<Jar>(sourceTaskName) {
        archiveClassifier.set("sources")
        group = "build"

        from(sourceSets["main"].allSource)
    }
    tasks["jar"].dependsOn(sourceTaskName)
}

// 排除主项目的构建脚本
subprojects {
    dependencies {
        // 子项目自动依赖 kotlin 标准库
        implementation(kotlin("stdlib-jdk8"))
        // 子项目自动依赖重要数学和定义库
        implementation(files("../libs/simulator-0.0.1.jar"))
        implementation("org.mechdancer", "linearalgebra", "+")
        // 单元测试
        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
    }
}

// 主项目依赖项
dependencies {
    // 导出 kotlin 标准库
    api(kotlin("stdlib-jdk8"))
    // 导出子模块
    api(project(":common"))           // 日志器和临时成员注解
    api(project(":drivers"))          // 传感器驱动
    api(project(":locator"))          // 定位融合
    api(project(":obstacledetector")) // 障碍物检测与规避
    api(project(":pathfollower"))     // 循线控制算法
    api(project(":painter"))          // 调试与绘图功能
    // 导出外部依赖
    api(fileTree("libs"))
    api("org.mechdancer", "linearalgebra", "+")
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "+")
    api("net.java.dev.jna", "jna", "+")
    // 单元测试
    testImplementation("junit", "junit", "+")
    testImplementation(kotlin("test-junit"))
    // 其他测试
    testImplementation("com.google.protobuf", "protobuf-java", "2.6.1")
    testImplementation("org.zeromq", "jeromq", "0.5.1")
    testImplementation(fileTree("libs-test"))
}
