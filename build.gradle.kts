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

    // The platform. platform-database is deliberately absent: the proxy touches no SQL yet.
    implementation(libs.platform.api)
    implementation(libs.platform.common)
    implementation(libs.platform.config)
    implementation(libs.platform.messaging)
    implementation(libs.platform.proxy)

    // Velocity ships PacketEvents' Velocity module as a plugin dependency, not a shaded one.
    compileOnly(libs.packetevents.velocity)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.velocity.api)
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
    val shaded = "pl.landmc.proxy.libs"
    listOf(
        "eu.okaeri",
        "dev.rollczi.litecommands",
        "com.eternalcode.multification",
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

tasks.build {
    dependsOn(tasks.shadowJar)
}
