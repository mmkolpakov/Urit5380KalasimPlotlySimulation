plugins {
    kotlin("jvm") version "2.1.10"
}

group = "space.kscience"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://repo.kotlin.link")
    maven {
        name = "reposiliteRepositoryKscience"
        url = uri("https://maven.sciprog.center/kscience")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.holgerbrandl:kalasim:1.0.4")
    implementation("space.kscience:plotlykt-core:0.5.0")
    implementation("androidx.collection:collection-jvm:1.4.0")
    implementation("androidx.annotation:annotation:1.8.0")
    implementation(kotlin("stdlib"))
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}