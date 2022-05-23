import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.6.7"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
}

group = "de.uni-luebeck.itr"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_13

repositories {
    mavenCentral()
    maven {
        setUrl("https://jitpack.io")
    }
}

dependencies {
    val hapiVersion = "5.7.2"
     val medicatsVersion = "1.0.0"
    implementation("info.picocli:picocli-spring-boot-starter:4.6.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-base:$medicatsVersion")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-icd:$medicatsVersion")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-ops:$medicatsVersion")
    implementation("com.github.jpwiedekopf.medicats-dimdi-fhir:medicats-alphaid:$medicatsVersion")
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
