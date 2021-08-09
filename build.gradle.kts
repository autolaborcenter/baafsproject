import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins { kotlin("jvm") version "1.5.21" }

// 包括主项目的构建脚本
allprojects {
    apply(plugin = "kotlin")
    group = "cn.autolabor"
    version = "v0.1.1"
    repositories { mavenCentral() }
    dependencies {
        // 自动依赖 kotlin 标准库
        implementation(kotlin("stdlib-jdk8"))
        implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.1.1")
        // 单元测试
        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
    }
    java { withSourcesJar() }
}

// 排除主项目的构建脚本
subprojects {
    dependencies {
        // 子项目自动依赖重要数学和定义库
        implementation(files("../libs/linearalgebra-0.2.6-dev-2.jar"))
        implementation(files("../libs/simulator-0.0.3.jar"))
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
        duplicatesStrategy = INCLUDE
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
        include("*.autolabor.kts")
        into("$buildDir/libs")
    }
}
