import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
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

allprojects {
    apply(plugin = "kotlin")
    group = "cn.autolabor"
    repositories {
        mavenCentral()
        jcenter()
    }
    dependencies {
        val kotlinVersion = getKotlinPluginVersion()

        "implementation"("org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion)

        "testImplementation"("junit:junit:4.12")
        "testImplementation"("org.jetbrains.kotlin", "kotlin-test-junit", kotlinVersion)
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}

dependencies {
    implementation(fileTree("libs") { ".jar" in includes })
    implementation(kotlin("stdlib-jdk8"))
}
