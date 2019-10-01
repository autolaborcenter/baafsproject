version = "1.0-SNAPSHOT"

dependencies {
    // 日志与注解
    implementation(project(":common"))
    // 协程
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "+")
    // 调试与绘图
    implementation(project(":painter"))
}
