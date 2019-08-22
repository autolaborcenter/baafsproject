version = "1.0-SNAPSHOT"

dependencies {
    implementation(files("../libs/linearalgebra-0.2.5-dev-2.jar"))
    implementation(project(":locator"))
    testImplementation(project(":drivers"))
    testImplementation("net.java.dev.jna", "jna", "+")
    testImplementation(files("../libs/pm1sdk-v0.0.1.jar"))
    testImplementation(files("../libs-test/consoleparser-0.1.9.jar"))
}
