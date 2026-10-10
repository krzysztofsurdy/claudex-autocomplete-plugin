import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.ksurdy.claudeautocomplete"
version = providers.gradleProperty("pluginVersion").getOrElse("1.0.0")

sourceSets {
    create("uiTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val uiTestImplementation by configurations.getting

repositories {
    mavenCentral()
    intellijPlatform {
        localPlatformArtifacts()
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIdePath = providers.gradleProperty("localIdePath")
        if (localIdePath.isPresent) {
            local(localIdePath.get())
        } else {
            phpstorm("2025.3")
        }
        bundledPlugin("com.jetbrains.php")
        bundledPlugin("org.jetbrains.plugins.yaml")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = uiTestImplementation.name)
    }
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2")
    uiTestImplementation(kotlin("test-junit5"))
    uiTestImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    uiTestImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    uiTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
    "uiTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val uiTest by intellijPlatformTesting.testIdeUi.registering {
    task {
        val uiTestSourceSet = sourceSets.getByName("uiTest")
        testClassesDirs = uiTestSourceSet.output.classesDirs
        classpath = uiTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        dependsOn(tasks.buildPlugin)
        systemProperty("path.to.build.plugin", tasks.buildPlugin.get().archiveFile.get().asFile.absolutePath)
        systemProperty("ui.test.project", layout.projectDirectory.dir("src/uiTest/resources/php-project").asFile.absolutePath)
        providers.gradleProperty("uiTestIdeVersion").orNull?.let { systemProperty("ui.test.ide.version", it) }
        testLogging {
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
        languageVersion = KotlinVersion.KOTLIN_2_2
        apiVersion = KotlinVersion.KOTLIN_2_2
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            val localIdePath = providers.gradleProperty("localIdePath")
            if (localIdePath.isPresent) {
                local(localIdePath.get())
            } else {
                create(IntelliJPlatformType.PhpStorm, "2025.3.6.1")
                create(IntelliJPlatformType.PhpStorm, "2026.2.3")
            }
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginChannel").map { listOf(it) }.orElse(listOf("default"))
    }
}
