import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.ir.backend.js.compile

plugins {
    id("org.springframework.boot") version "2.6.7"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
}

group = "de.uni-luebeck.itcr"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
    maven {
        setUrl("https://jitpack.io")
    }
}

dependencies {
    val hapiVersion = "5.7.2"
    implementation("info.picocli:picocli-spring-boot-starter:4.6.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-base:master-SNAPSHOT")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-icd:master-SNAPSHOT")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-ops:master-SNAPSHOT")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-alphaid:master-SNAPSHOT")
    implementation("com.github.jpwiedekopf:fhir-claml:master-SNAPSHOT")
    // TODO: 16/05/22 figure out re-packaging FHIR-ClaML
    // the JAR does not expose the actual model etc.
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:$hapiVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "13"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
