plugins {
    application
    java
}

group = "com.gcanalyzer"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("com.microsoft.gctoolkit:gctoolkit-api:3.7.0")
    implementation("com.microsoft.gctoolkit:gctoolkit-parser:3.7.0")
    implementation("com.microsoft.gctoolkit:gctoolkit-vertx:3.7.0")
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.mitchtalmadge:ascii-data:1.4.0")
}

application {
    mainClass.set("com.gcanalyzer.Main")
    applicationName = "gc-analyzer"
    // Netty (pulled in by gctoolkit-vertx) calls sun.misc.Unsafe and uses
    // native access. On JDK 23+ this produces startup warnings that pollute
    // the report. Both flags exist on JDK 23+ and are recognized by the
    // current JDK 25 toolchain, so we apply them to both the `run` task and
    // the installDist launcher via applicationDefaultJvmArgs. If the
    // toolchain is ever downgraded below JDK 23, move these onto the
    // `startScripts` task instead — `--sun-misc-unsafe-memory-access` will
    // otherwise fail the `run` task with "Unrecognized option".
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow"
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.register("dist") {
    description = "Build a self-contained distribution with a minimal JRE via jlink"
    group = "distribution"
    dependsOn("installDist")

    val installDir = layout.buildDirectory.dir("install/gc-analyzer")
    val outputDir = layout.buildDirectory.dir("dist/gc-analyzer")
    val tarFile = layout.buildDirectory.file("dist/gc-analyzer-${project.version}-${osArch()}.tar.gz")
    val javaHome = javaToolchains
        .launcherFor(java.toolchain)
        .map { it.metadata.installationPath.asFile.absolutePath }
    inputs.dir(installDir)
    outputs.file(tarFile)

    doLast {
        val outDir = outputDir.get().asFile
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()

        val runtimeDir = outDir.resolve("runtime")
        val jlinkBin = "${javaHome.get()}/bin/jlink"

        // Build a minimal JRE with jlink — include the modules the app needs
        ProcessBuilder(
            jlinkBin,
            "--add-modules", "java.base,java.logging,java.xml,java.management,java.sql,jdk.unsupported",
            "--strip-debug",
            "--no-man-pages",
            "--no-header-files",
            "--compress", "zip-6",
            "--output", runtimeDir.absolutePath
        ).inheritIO().start().waitFor().let { exit ->
            if (exit != 0) throw GradleException("jlink failed with exit code $exit")
        }

        // Copy app JARs
        val libDir = outDir.resolve("lib")
        val sourceLibDir = installDir.get().asFile.resolve("lib")
        sourceLibDir.listFiles()?.forEach { it.copyTo(libDir.resolve(it.name), overwrite = true) }
            ?: throw GradleException("No JARs found in ${sourceLibDir.absolutePath}")

        // Create launcher script
        val binDir = outDir.resolve("bin")
        binDir.mkdirs()
        val launcher = binDir.resolve("gc-analyzer")
        val launcherScript = """
            |#!/usr/bin/env sh
            |set -e
            |SCRIPT_DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)"
            |APP_HOME="${'$'}(cd "${'$'}SCRIPT_DIR/.." && pwd)"
            |JAVA_EXE="${'$'}APP_HOME/runtime/bin/java"
            |CLASSPATH="${'$'}APP_HOME/lib/*"
            |exec "${'$'}JAVA_EXE" \
            |  --enable-native-access=ALL-UNNAMED \
            |  --sun-misc-unsafe-memory-access=allow \
            |  -cp "${'$'}CLASSPATH" \
            |  com.gcanalyzer.Main "${'$'}@"
        """.trimMargin() + "\n"
        launcher.writeText(launcherScript)
        launcher.setExecutable(true)

        // Create tarball
        val tar = tarFile.get().asFile
        tar.parentFile.mkdirs()
        ProcessBuilder("tar", "czf", tar.absolutePath, outDir.name)
            .directory(outDir.parentFile)
            .inheritIO().start().waitFor().let { exit ->
                if (exit != 0) throw GradleException("tar failed with exit code $exit")
            }

        logger.lifecycle("Distribution: ${tar.absolutePath}")
        logger.lifecycle("  Unpack and run: bin/gc-analyzer <log>")
    }
}

fun osArch(): String {
    val os = System.getProperty("os.name").lowercase().let {
        when {
            "mac" in it || "darwin" in it -> "macos"
            "linux" in it -> "linux"
            "win" in it -> "windows"
            else -> it.replace(" ", "")
        }
    }
    val arch = System.getProperty("os.arch").let {
        when (it) {
            "aarch64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> it
        }
    }
    return "$os-$arch"
}
