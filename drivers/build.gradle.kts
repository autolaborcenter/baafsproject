dependencies {
    implementation("com.fazecast", "jSerialComm", "+")
    implementation("net.java.dev.jna", "jna", "+")
    implementation(project(":common"))
    implementation(project(":painter")) // 调试与绘图功能
}
