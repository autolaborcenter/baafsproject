dependencies {
    // 网络工具
    testImplementation(kotlin("reflect"))
    testImplementation("org.slf4j", "slf4j-api", "+")
    testImplementation("org.mechdancer", "dependency", "+")
    testImplementation("org.mechdancer", "remote", "+")
    testImplementation(fileTree("../libs/consoleparser-0.1.9.jar"))
}
