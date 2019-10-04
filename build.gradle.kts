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
        implementation(files("../libs/simulator-0.0.1.jar"))
    }
}
