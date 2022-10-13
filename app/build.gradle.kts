plugins {
    kotlin("jvm") version "1.7.20"
    id("io.ktor.plugin") version "2.1.2"
}

application {
    mainClass.set("aap.dokumenter.app.AppKt")
}

val aapLibsVersion = "3.5.6"
val ktorVersion = "2.1.2"

dependencies {
    implementation("com.github.navikt.aap-libs:ktor-auth-behalfof:$aapLibsVersion")
    implementation("com.github.navikt.aap-libs:ktor-utils:$aapLibsVersion")

    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.9.5")

    implementation("ch.qos.logback:logback-classic:1.4.4")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")

}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "18"
    }

    withType<Test> {
        reports.html.required.set(false)
        useJUnitPlatform()
    }
}