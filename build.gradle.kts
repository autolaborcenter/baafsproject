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

allprojects {
    apply(plugin = "kotlin")
    group = "cn.autolabor"
    repositories {
        mavenCentral()
        jcenter()
    }
}

subprojects {
    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions { jvmTarget = "1.8" }
    }
}

dependencies {
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
tasks.withType<KotlinCompile> {
    kotlinOptions { jvmTarget = "1.8" }
}
