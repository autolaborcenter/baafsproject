plugins {
    application
}

// 主项目依赖项
dependencies {
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("main-kts"))
    implementation(project(":library"))
}

application {
    mainClassName = "Scripting"
}
