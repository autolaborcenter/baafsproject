dependencies {
    // 日志与注解
    implementation(project(":common"))
    // 控制台指令解析
    implementation(files("../libs/consoleparser-0.1.9.jar"))
    // 调试与绘图
    implementation(project(":painter"))
}
