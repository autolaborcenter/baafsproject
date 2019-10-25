import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/jcenter")
    }
}

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
        implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.2")
        // 单元测试
        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
    }
    // 源码导出任务
    with("sourcesJar") {
        tasks["jar"].dependsOn(this)
        tasks.register<Jar>(this) {
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
        implementation(files("../libs/simulator-0.0.2.jar"))
    }
}

// 主项目依赖项
dependencies {
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("main-kts"))
    implementation(project(":library"))
}

"scripting-application".let { name ->
    // 打包任务
    tasks["build"].dependsOn(name)
    tasks.register<Jar>(name) {
        manifest { attributes("Main-Class" to "MainKt") }
        group = JavaBasePlugin.BUILD_TASK_NAME
        description = "pack jar to run script"
        archiveClassifier.set(name)
        from(sourceSets.main.get().output,
            configurations.runtimeClasspath.get()
                .map { if (it.isDirectory) it else zipTree(it) })
    }
}

"copyConfiguration".let { name ->
    tasks["build"].dependsOn(name)
    tasks.register<Copy>(name) {
        group = JavaBasePlugin.BUILD_TASK_NAME
        description = "copy configuration files to target direction"
        from("$rootDir")
        include("conf/**")
        include("*.autolabor.kts")
        into("$buildDir/libs")
    }
}
