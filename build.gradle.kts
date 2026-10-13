plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}
group = "com.mysticalmanor"
version = "2.0"
repositories { mavenCentral() }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.ktor:ktor-server-core-jvm:3.0.3")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("org.postgresql:postgresql:42.7.5")
}
application { mainClass.set("WebMainKt") }
kotlin { jvmToolchain(17) }
