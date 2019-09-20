version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":common"))
    // for transform
    implementation(files("../libs/common-extension-0.1.0-3.jar"))
    implementation(files("../libs/common-extension-0.1.0-3-sources.jar"))
    implementation(files("../libs/common-collection-0.1.0-3.jar"))
    implementation(files("../libs/common-collection-0.1.0-3-sources.jar"))
    implementation(project(":transform"))
    // for framework
    implementation(files("../libs/autolabor_core-1.0.0.5.jar"))
    // for gazebo
    testImplementation("com.google.protobuf", "protobuf-java", "2.6.1")
    testImplementation(files("../libs/autolabor_core-1.0.0.4.jar"))
    testImplementation(files("../libs-test/gazebo_plugin-1.0-SNAPSHOT.jar"))
    // for painter
    testImplementation(project(":painter"))
}
