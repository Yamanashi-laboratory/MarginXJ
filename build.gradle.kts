plugins {
    application
    java
}

group = "com.ynu"
version = "0.1.0-SNAPSHOT"

// Runs on any JDK 21+; bytecode is pinned to 21 so the workplace toolchain can consume it.

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.picocli)
    annotationProcessor(libs.picocli.codegen)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.archunit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "com.ynu.marginx.presentation.MarginXCommand"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    // Forward simulator locations to the test JVM: RealJosimIT stays skipped unless one is given.
    listOf("marginx.it.josim", "marginx.josim.command").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
