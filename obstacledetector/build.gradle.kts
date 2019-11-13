dependencies {
    implementation(project(":common"))
    implementation(project(":drivers"))
    // for framework
    implementation(files("../libs/autolabor_core-1.0.0.5.jar"))
    implementation(files("../libs/common-extension-0.1.0-3.jar"))
    testImplementation(fileTree("../libs-test"))
    testImplementation("com.google.protobuf", "protobuf-java", "2.6.1")
    testImplementation("org.zeromq", "jeromq", "0.5.1")
}
