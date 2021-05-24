plugins {
    kotlin("jvm") version "1.4.31"
}



repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    jcenter()
}

val mindVer = "v126.2"
val utilsVer = "v0.3.3"
val exposedVer = "0.31.1"
val junitVersion = "5.6.1"
val klaxonVer = "5.5"
val postgreVer = "42.2.10"
val jsoupVer = "1.12.1"
val discord4jVer = "3.1.5"
val codecVer = "1.11"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    compileOnly("com.github.Anuken.Arc:arc-core:$mindVer")
    compileOnly("com.github.Anuken.Mindustry:core:$mindVer")

    implementation("com.beust:klaxon:$klaxonVer")
    implementation("com.github.jakubDoka:mindustry-plugin-utils:$utilsVer")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVer")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVer")
    implementation("org.postgresql:postgresql:$postgreVer")
    implementation("com.discord4j:discord4j-core:$discord4jVer")
    implementation("commons-codec:commons-codec:$codecVer")

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
        from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }

    "test"(Test::class) {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
        reports.html.isEnabled = true
    }
}
