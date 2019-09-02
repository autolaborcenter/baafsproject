version = "1.0-SNAPSHOT"

dependencies {
    // for transform
    implementation(files("../libs/linearalgebra-0.2.5-dev-2.jar"))
    implementation(files("../libs/common-extension-0.1.0-3.jar"))
    implementation(files("../libs/common-extension-0.1.0-3-sources.jar"))
    implementation(files("../libs/common-collection-0.1.0-3.jar"))
    implementation(files("../libs/common-collection-0.1.0-3-sources.jar"))
    implementation(project(":transform"))
}