pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    // Provisions a JDK 25 when the machine does not have one; Velocity 4 and the platform's
    // proxy module both publish Java 25 bytecode.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        // The platform is consumed as a published artifact, not as an included build: every
        // LandMC project is its own repository. mavenLocal covers development until the
        // platform is published to GitHub Packages.
        mavenLocal()

        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroup("com.velocitypowered")
                includeGroup("com.mojang")
                includeGroup("net.md-5")
            }
        }
        maven("https://repo.codemc.io/repository/maven-releases/") {
            content { includeGroup("com.github.retrooper") }
        }
        maven("https://repo.panda-lang.org/releases/") {
            content { includeGroup("dev.rollczi") }
        }
        maven("https://repo.eternalcode.pl/releases/") {
            content { includeGroup("com.eternalcode") }
        }
        maven("https://storehouse.okaeri.eu/repository/maven-public/") {
            content { includeGroup("eu.okaeri") }
        }
    }
}

rootProject.name = "landmc-proxy"
