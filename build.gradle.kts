import java.util.zip.ZipFile

plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Velocity 4 and platform-proxy are compiled for Java 25; --release 21 cannot read them.
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked", "-parameters"))
}

configurations.runtimeClasspath {
    // Velocity already provides these, and Jedis drags in versions that would shadow them
    // inside the plugin jar - slf4j-api 1.7.x against the proxy's 2.x is enough to break
    // logging. Excluding them here keeps the shaded jar free of both.
    exclude(group = "com.google.code.gson")
    exclude(group = "org.slf4j", module = "slf4j-api")
}

dependencies {
    compileOnly(libs.velocity.api)
    // Generates velocity-plugin.json from the @Plugin annotation, so the descriptor cannot
    // drift away from the code that declares it.
    annotationProcessor(libs.velocity.api)

    // The platform. The database arrived with the friends list - the first thing on the proxy
    // that has to outlive a session.
    implementation(libs.platform.api)
    implementation(libs.platform.common)
    implementation(libs.platform.config)
    implementation(libs.platform.database)
    implementation(libs.platform.messaging)
    implementation(libs.platform.proxy)

    // Velocity ships PacketEvents' Velocity module as a plugin dependency, not a shaded one.
    compileOnly(libs.packetevents.velocity)

    // Optional integration: read through RankProvider, which tolerates it being absent.
    compileOnly(libs.luckperms.api)

    // The default database for the friends list. Another driver can be dropped into the
    // proxy's own plugin folder; this one ships so the feature works out of the box.
    runtimeOnly(libs.h2)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.velocity.api)
    testImplementation(libs.luckperms.api)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.slf4j.simple)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.shadowJar {
    archiveFileName = "landmc-proxy.jar"

    // Velocity has no runtime library loader, so everything the plugin needs that the proxy
    // does not already provide has to travel inside the jar.
    //
    // Relocated: libraries another plugin might also shade at a different version. Not
    // relocated: Adventure, Gson and SLF4J, which Velocity provides and which must stay the
    // proxy's own classes, and pl.landmc.platform, which only this plugin loads.
    //
    // H2 is deliberately not relocated either, and that one is not a matter of taste. H2's
    // MVStore writes Java class names into the database file, so a relocated build produces a
    // file that only that build can open: not the H2 console, not a backup tool, and not the
    // next version if the shade prefix ever changes. Verified by opening a proxy-written file
    // with the stock h2 jar - it fails on a missing pl.landmc.proxy.libs.org.h2 class.
    val shaded = "pl.landmc.proxy.libs"
    listOf(
        "eu.okaeri",
        "dev.rollczi.litecommands",
        "com.eternalcode.multification",
        // The connection pool and the ORM, which any other plugin may also shade.
        "com.zaxxer.hikari",
        "com.j256.ormlite",
        // The whole Jedis tree, not just redis.clients.jedis: it also ships
        // redis.clients.authentication, which a narrower rule leaves unrelocated.
        "redis.clients",
        // Jedis' own transitive dependencies.
        "org.json",
        "org.apache.commons.pool2",
        "org.yaml.snakeyaml",
    ).forEach { relocate(it, "$shaded.$it") }

    // Signatures of the jars we merge are meaningless once relocated, and a stray one makes
    // the JVM reject the plugin jar.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class")

    // Annotation classes the compiler needed and the runtime does not.
    exclude("org/jetbrains/annotations/**", "org/intellij/lang/**")

    mergeServiceFiles()
}

/**
 * Fails the build if H2 ends up relocated after all.
 *
 * H2 writes Java class names into the .mv.db file, so a relocated build produces a database
 * only that build can open - and the failure surfaces on somebody's server later, as a plugin
 * that will not start on a database it wrote itself. Adding "org.h2" back to the relocation
 * list is a one-line change with no visible consequence at build time, so the build checks the
 * jar rather than relying on the comment being read.
 */
val checkDatabaseNotRelocated = tasks.register("checkDatabaseNotRelocated") {
    group = "verification"
    description = "Fails when H2 ends up relocated, which would tie the database file to one jar."
    dependsOn(tasks.shadowJar)

    val jarFile = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(jarFile)

    doLast {
        val relocated = ZipFile(jarFile.get().asFile).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("pl/landmc/proxy/libs/org/h2/") }
                .take(1)
                .toList()
        }

        check(relocated.isEmpty()) {
            "H2 is relocated (${relocated.first()}). A database written by this jar could then " +
                "only be opened by this jar - see the note in the shadowJar block."
        }
    }
}

tasks.named("check") { dependsOn(checkDatabaseNotRelocated) }

tasks.build {
    dependsOn(tasks.shadowJar)
}
