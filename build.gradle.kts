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
