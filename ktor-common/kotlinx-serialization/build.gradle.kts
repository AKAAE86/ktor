val serialization_version: String by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-common:ktor-serialization"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
        }
    }
}
