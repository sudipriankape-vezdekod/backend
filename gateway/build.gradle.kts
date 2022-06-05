val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val mainClassName = "vk.sudipriankape.ApplicationKt"

plugins {
    application
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "vk.sudipriankape"
version = "0.0.1"
application {
    mainClass.set("$mainClassName")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "$mainClassName"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(
        configurations
            .runtimeClasspath
            .get()
            .map(::zipTree)
    )
}
