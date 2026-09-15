plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation("javax.inject:javax.inject:1")
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
}
