description = ""

val jackson_version: String by project.extra
val jackson_kotlin_version: String by project.extra

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-common:ktor-serialization"))
                api("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
                api("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_kotlin_version")
            }
        }
    }
}
