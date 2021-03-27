
val serialization_version: String by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-client:ktor-client-core"))
        }
    }
}
