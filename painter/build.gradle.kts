// 主项目依赖项
dependencies {
    implementation(project(":common"))
    // 导出网络工具的依赖
    api("org.slf4j", "slf4j-api", "+")
    api(fileTree("libs"))
}
