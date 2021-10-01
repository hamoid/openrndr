plugins {
    kotlin("jvm")
}

val lwjglVersion: String by rootProject.extra

dependencies {
    implementation(project(":openrndr-application"))
    implementation(project(":openrndr-event"))
    implementation(project(":openrndr-core"))
    implementation("org.lwjgl:lwjgl-nfd:$lwjglVersion")
}
