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
    tasks.withType<KotlinCompile> {
        kotlinOptions { jvmTarget = "1.8" }
    }
    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    task<Jar>("sourcesJar") {
        classifier = "sources"
        group = "build"

        from(sourceSets["main"].allSource)
    }
    tasks["jar"].dependsOn("sourcesJar")
}

subprojects {
    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        testImplementation("junit", "junit", "+")
        testImplementation(kotlin("test-junit"))
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
