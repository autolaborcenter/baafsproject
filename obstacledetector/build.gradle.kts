dependencies {
    implementation(project(":common"))
    // for framework
    implementation(files("../libs/autolabor_core-1.0.0.5.jar"))
    // for gazebo
    testImplementation("com.google.protobuf", "protobuf-java", "2.6.1")
    testImplementation(files("../libs/autolabor_core-1.0.0.4.jar"))
    testImplementation(files("../libs-test/gazebo_plugin-1.0-SNAPSHOT.jar"))
}
