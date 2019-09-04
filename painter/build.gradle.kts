version = "1.0-SNAPSHOT"

// 主项目依赖项
dependencies {
    // 导出必要的依赖
    implementation(kotlin("stdlib-jdk8"))

    api("org.slf4j", "slf4j-api", "+")
    api(kotlin("reflect"))
    api(fileTree("../libs-test/dependency-0.1.0-rc-3.jar"))
    api(fileTree("../libs-test/remote-0.2.1-dev-13.jar"))
}
