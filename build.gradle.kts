plugins {
    application
    java
    alias(libs.plugins.graalvm.native)
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

// Distributable 1: a single executable, so users need no JRE. picocli-codegen already emits the
// reflection metadata into META-INF/native-image, leaving only our own resource to declare.
graalvmNative {
    // Use whichever JDK runs Gradle (it must be a GraalVM); toolchain resolution is brittle here.
    toolchainDetection = false
    binaries {
        named("main") {
            imageName = "marginx"
            // SimulatorProperties.load() reads this through getResourceAsStream. Left out of the
            // image it would silently degrade to the hardcoded defaults.
            resources.includedPatterns.add("application\\.properties")
            buildArgs.addAll(
                // The default target assumes recent CPU instructions; stay portable instead.
                "-march=compatibility",
            )
        }
    }
}

// Distributable 2: a runnable JAR. Native images need a machine per OS, so this covers the
// platforms CI does not build (Linux arm64 and the like) for anyone with a JDK 21+.
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { if (it.isDirectory) it else zipTree(it) }
    })
    // Signatures from the dependencies no longer verify once repackaged.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn(fatJar)
}
