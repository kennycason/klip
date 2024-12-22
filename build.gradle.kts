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

    // Ktor Serialization
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // AWS SDK for S3
    implementation("aws.sdk.kotlin:s3:1.3.99")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    // Image processing
    implementation("com.twelvemonkeys.imageio:imageio-core:$imageIoVersion")
    implementation("com.twelvemonkeys.imageio:imageio-bmp:$imageIoVersion")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:$imageIoVersion")

    // Logging
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")


}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

application {
    mainClass.set("AppKt") // Replace with your main class if different
}
