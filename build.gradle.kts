plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("io.ktor.plugin") version "3.0.3"
}

group = "klip"
version = "1.0.0"

val kotlinVersion = "2.0.0"
val ktorVersion = "3.0.3"
val kotlinxSerializationVersion = "1.7.1"
val kotlinxSerializationVersionVersion = "1.7.3"
val log4jVersion = "2.24.3"
val imageIoVersion = "3.12.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server Core and CIO Engine
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // Ktor Serialization
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // AWS SDK for S3
    implementation("aws.sdk.kotlin:s3:1.3.99")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Logging
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.strikt:strikt-core:0.35.1")

    testImplementation("io.ktor:ktor-client-core:$ktorVersion") // client code used in ITests
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

application {
    mainClass.set("klip.AppKt") // Replace with your main class if different
}
