/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    `maven-publish`
    id("net.researchgate.release") version "2.8.1"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:3.13.1")
    implementation("com.google.code.gson:gson:2.8.2")
    implementation("net.sf.saxon:Saxon-HE:9.9.1-1")
    implementation("org.ccil.cowan.tagsoup:tagsoup:1.2.1")
    implementation("commons-io:commons-io:2.6")
    implementation("commons-codec:commons-codec:1.12")
    implementation("commons-cli:commons-cli:1.4")
    implementation("org.slf4j:slf4j-simple:1.7.21")
    implementation("com.sparkjava:spark-core:2.7.2")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.31")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.4.31")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
}

group = "me.itdog"
version = "0.0.3-SNAPSHOT"
description = "rssthis"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

release {
    failOnUpdateNeeded = false
}

fun checkUpdateNeeded(): Boolean {
    return true
}
