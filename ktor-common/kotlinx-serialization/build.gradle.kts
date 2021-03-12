val serialization_version = extra["serialization_version"]

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-common:ktor-serialization"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
        }
    }
}
