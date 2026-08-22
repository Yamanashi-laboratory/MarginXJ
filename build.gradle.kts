plugins {
    application
    java
    alias(libs.plugins.openjfx)
}

group = "com.ynu"
// Overridable so a tagged CI build can stamp the installer: ./gradlew jpackage -Pversion=0.2.0
version = (findProperty("version") as String?)?.takeUnless { it == "unspecified" } ?: "0.1.0-SNAPSHOT"

// Runs on any JDK 21+; bytecode is pinned to 21 so the workplace toolchain can consume it.

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.picocli)
    annotationProcessor(libs.picocli.codegen)
    // The netlist editor: a styled text area, which JavaFX itself does not have.
    implementation(libs.richtextfx)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.archunit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    // The router: no arguments opens the GUI, anything else is the command line.
    mainClass = "com.ynu.marginx.presentation.MarginX"
}

// The GUI (netlist editor and margin chart) is built on JavaFX; the plugin resolves the
// platform-specific artifacts for whichever machine runs the build.
// Read through the catalog API rather than the libs.versions.* accessor: inside this script the
// accessor resolves against the extension container the JavaFX plugin adds and fails to compile.
val javafxVersion = the<VersionCatalogsExtension>().named("libs")
    .findVersion("openjfx").orElseThrow().requiredVersion

javafx {
    version = javafxVersion
    // javafx.swing is only here for SwingFXUtils, which is how a chart snapshot becomes a PNG.
    modules("javafx.controls", "javafx.fxml", "javafx.swing")
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
    listOf("marginx.it.josim", "marginx.it.jsim",
            "marginx.josim.command", "marginx.jsim.command").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// Distributable 1: a runnable JAR, for anyone with a JDK 21+ and for platforms we ship no
// installer for. It also feeds jpackage below.
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier = "all"
    // EXCLUDE applies to directory entries too, and the JavaFX jars all carry javafx/scene/: the
    // first one copied claimed that directory and everything under it from the later jars was
    // dropped, which silently left javafx.controls out of the jar. Empty directories are not
    // wanted in an archive anyway, so skipping them removes the collision entirely.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    includeEmptyDirs = false
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { if (it.isDirectory) it else zipTree(it) }
    })
    // Signatures from the dependencies no longer verify once repackaged, and each JavaFX jar
    // brings its own module-info that means nothing on a classpath.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}

tasks.named("assemble") {
    dependsOn(fatJar)
}

/** Where the WiX v3 installer puts candle.exe, or null when it is not installed. */
fun wixToolsDirectory(): String? = sequenceOf("ProgramFiles(x86)", "ProgramFiles")
    .mapNotNull(System::getenv)
    .flatMap { programFiles -> File(programFiles).listFiles { file: File -> file.name.startsWith("WiX Toolset") }
        ?.asSequence() ?: emptySequence() }
    .map { File(it, "bin") }
    .firstOrNull { File(it, "candle.exe").isFile }
    ?.absolutePath

// Distributable 2: an OS-native installer with a bundled runtime, so users need no JDK.
// Neither JoSIM nor JSIM is bundled - see docs/adr/0001-distribution-strategy.md.
//
// Skeleton for now: it has never produced an installer. Once the JavaFX GUI lands, check whether
// the fat jar alone is enough or whether the JavaFX native libraries need --module-path / jlink.

// jpackage copies every jar in --input, so stage the fat jar on its own.
val jpackageInput = tasks.register<Sync>("jpackageInput") {
    from(fatJar)
    into(layout.buildDirectory.dir("jpackage/input"))
}

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Builds an installer (.msi on Windows, .deb on Linux) around the fat jar."
    dependsOn(jpackageInput)

    val operatingSystem = System.getProperty("os.name").lowercase()
    val windows = operatingSystem.startsWith("windows")
    val defaultType = when {
        windows -> "msi"
        operatingSystem.startsWith("mac") -> "dmg"
        else -> "deb"
    }
    // -Pjpackage.type=app-image produces the unpacked application CI zips as the portable build.
    val type = (findProperty("jpackage.type") as String? ?: defaultType)

    // jpackage ships with the JDK that runs Gradle, which must therefore be 21+.
    val jpackageTool = File(System.getProperty("java.home"), "bin/jpackage" + if (windows) ".exe" else "")
    executable = jpackageTool.absolutePath

    // jpackage shells out to WiX to build an MSI and only looks for it on PATH, but the WiX
    // installer does not put itself there. Finding it saves every developer the same detour.
    if (windows && type == "msi") {
        wixToolsDirectory()?.let { wix -> environment("PATH", wix + File.pathSeparator + System.getenv("PATH")) }
    }

    val inputDir = layout.buildDirectory.dir("jpackage/input").get().asFile
    val outputDir = layout.buildDirectory.dir("jpackage/$type").get().asFile
    outputs.dir(outputDir)

    argumentProviders.add(CommandLineArgumentProvider {
        outputDir.mkdirs()
        buildList {
            addAll(listOf("--type", type))
            addAll(listOf("--name", "MarginXJ"))
            // Shown in Add/Remove Programs and in the package metadata. Without it jpackage
            // writes "Unknown", which is not what other labs should see when they install this.
            addAll(listOf("--vendor", "Yamanashi Lab."))
            // MSI rejects a qualifier such as -SNAPSHOT: the version must be numeric.
            addAll(listOf("--app-version", version.toString().substringBefore('-')))
            addAll(listOf("--input", inputDir.absolutePath))
            addAll(listOf("--main-jar", fatJar.get().archiveFileName.get()))
            addAll(listOf("--main-class", application.mainClass.get()))
            addAll(listOf("--dest", outputDir.absolutePath))
            if (windows) {
                // MarginXJ is a command-line tool as much as a window: without a console the
                // launcher would swallow everything the CLI prints, and every startup error too.
                add("--win-console")
            }
            if (type == "deb") {
                // Debian wants a contact behind the vendor name, or it writes "Unknown".
                addAll(listOf("--linux-deb-maintainer", "syouc9@yahoo.co.jp"))
            }
            if (type == "msi") {
                add("--win-dir-chooser")
                // Stable across releases, otherwise every MSI installs alongside the previous one.
                addAll(listOf("--win-upgrade-uuid", "4f18fd88-60c1-4655-9e0d-4792021cb2ee"))
            }
        }
    })
}
