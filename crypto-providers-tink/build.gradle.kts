import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":crypto-spi", "default"))
    implementation(platform("software.amazon.awssdk:bom:2.15.0"))
    implementation("software.amazon.awssdk:kms")
    implementation("com.google.crypto.tink:tink-android:1.7.0")
    implementation("com.google.crypto.tink:tink-awskms:1.7.0")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:localstack:1.17.6")
    testImplementation("com.amazonaws:aws-java-sdk-s3:1.11.914")
    testImplementation("org.testcontainers:testcontainers:1.17.5")
    testImplementation("org.testcontainers:junit-jupiter:1.17.6")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "18"
}

tasks.register("prepareKotlinBuildScriptModel") {}

application {
    mainClass.set("pi2schema.crypto.providers.tink.TinkKeyStore")
}
