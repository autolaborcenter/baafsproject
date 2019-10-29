// 主项目依赖项
dependencies {
    implementation(project(":common"))
    // 导出网络工具的依赖
    api(kotlin("reflect"))
    api("org.slf4j", "slf4j-api", "+")
    api("org.mechdancer", "dependency", "+")
    api("org.mechdancer", "remote", "+")
}
