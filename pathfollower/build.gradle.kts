version = "1.0-SNAPSHOT"

dependencies {
    // for transform
    implementation(files("../libs/linearalgebra-0.2.5-dev-2.jar"))
    implementation(files("../libs/common-extension-0.1.0-3.jar"))
    implementation(files("../libs/common-extension-0.1.0-3-sources.jar"))
    implementation(files("../libs/common-collection-0.1.0-3.jar"))
    implementation(files("../libs/common-collection-0.1.0-3-sources.jar"))
    implementation(project(":transform"))

    implementation(project(":locator"))
    testImplementation(project(":drivers"))
    testImplementation("net.java.dev.jna", "jna", "+")
    testImplementation(files("../libs/pm1sdk-v0.0.1.jar"))
    testImplementation(files("../libs-test/consoleparser-0.1.9.jar"))
}
