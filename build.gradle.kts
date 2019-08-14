import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
    `build-scan`
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
    publishAlways()
}

version = "1.0-SNAPSHOT"

val libs = fileTree("libs")
allprojects {
    apply(plugin = "kotlin")
    group = "cn.autolabor"
    repositories {
        mavenCentral()
        jcenter()
    }
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation(libs)

        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}

dependencies {
    implementation(fileTree("libs") { ".jar" in includes })
}
