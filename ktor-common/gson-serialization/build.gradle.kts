description = ""

val gson_version: String by project.extra
val kotlin_version: String by project.extra

kotlin.sourceSets.jvmMain {
    dependencies {
        api(project(":ktor-common:ktor-serialization"))
        api("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
        api("com.google.code.gson:gson:$gson_version")
    }
}
