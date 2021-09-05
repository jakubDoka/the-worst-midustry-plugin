import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.0"
}



repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

val mindVer = "v129.1"
val utilsVer = "v0.4.3"
val exposedVer = "0.31.1"
val junitVersion = "5.6.1"
val klaxonVer = "5.5"
val postgreVer = "42.2.10"
val jsoupVer = "1.12.1"
val discord4jVer = "3.1.5"
val codecVer = "1.11"
val corotineVer = "1.5.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    compileOnly("com.github.Anuken.Mindustry:core:$mindVer")

    implementation("com.beust:klaxon:$klaxonVer")
    implementation("com.github.jakubDoka:mindustry-plugin-utils:$utilsVer")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVer")
    implementation("org.postgresql:postgresql:$postgreVer")
    implementation("com.discord4j:discord4j-core:$discord4jVer")
    implementation("commons-codec:commons-codec:$codecVer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$corotineVer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$corotineVer")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    jar {
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
            exclude("**/META-INF/*.SF")
            exclude("**/META-INF/*.DSA")
            exclude("**/META-INF/*.RSA")
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    "test"(Test::class) {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
        reports.html.isEnabled = true
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}