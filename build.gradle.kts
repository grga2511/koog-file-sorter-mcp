plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.11.1")
    implementation("ai.koog:agents-core:1.3.0")
    implementation("ai.koog:agents-mcp-jvm:1.3.0-beta")
    implementation("ai.koog:prompt-executor-llms-all:1.3.0-beta")
    implementation("ai.koog:http-client-ktor:1.3.0")
}

application {
    mainClass.set("FileSorterAgent")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}