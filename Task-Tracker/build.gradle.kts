import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.0"
}

group = "tracker"
version = "1.0"

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

val experimentJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Замеры декартова дерева на 10^4 и 10^5 и сравнение с TreeMap"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("tracker.bench.ComparisonBenchmarkKt")
    args(layout.buildDirectory.dir("reports/benchmark").get().asFile.path)
    jvmArgs(listOf("-Xmx2g") + experimentJvmArgs)
}

tasks.register<JavaExec>("race") {
    group = "verification"
    description = "Гонка потоков: запись в дерево без защиты и с synchronized"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("tracker.bench.RaceExperimentKt")
    args(layout.buildDirectory.file("reports/race/race.txt").get().asFile.path)
    jvmArgs(experimentJvmArgs)
}

tasks.register<JavaExec>("formats") {
    group = "verification"
    description = "Сравнение форматов CSV, JSON и бинарного на 10^4 задачах"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("tracker.bench.FormatExperimentKt")
    args(layout.buildDirectory.dir("reports/formats").get().asFile.path)
    jvmArgs(experimentJvmArgs)
}

compose.desktop {
    application {
        mainClass = "tracker.MainKt"

        val dynamicVersion = (project.findProperty("appVersion") as? String)?.removePrefix("v") ?: "1.0.0"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TaskTracker"
            packageVersion = dynamicVersion
        }
    }
}
