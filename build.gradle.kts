import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
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

        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
    }
}

// 主项目依赖项
dependencies {
    // 导出必要的依赖
    api(kotlin("stdlib-jdk8"))
    api(fileTree("libs"))
    api(project(":drivers"))
    api(project(":locator"))
    api(project(":pathfollower"))

    testImplementation("junit", "junit", "+")
    testImplementation(kotlin("test-junit"))

    testImplementation("org.slf4j", "slf4j-api", "+")
    testImplementation("net.java.dev.jna", "jna", "+")
    testImplementation(kotlin("reflect"))
    testImplementation(fileTree("libs-test"))
}
