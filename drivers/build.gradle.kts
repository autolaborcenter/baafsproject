version = "1.0-SNAPSHOT"

dependencies {
    implementation("com.fazecast", "jSerialComm", "+")
    implementation(files("../libs/pm1sdk-v0.0.1.jar"))
    implementation(project(":common"))
    implementation(files("../libs/linearalgebra-0.2.5-dev-2.jar"))
}
