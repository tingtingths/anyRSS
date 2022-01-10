plugins {
    java
    kotlin("jvm") version "1.4.31"
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

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.register("release") {
    val version: String = project.property("version") as String
    val regex = Regex("^(?<major>[0-9]+)\\.(?<minor>[0-9]+)\\.(?<patch>[0-9]+)(?:-(?<qualifier>.+))?\$")
    println("version=${version}")
    val matched = regex.matchEntire(version)
    if (matched == null) {
        println("Unable to parse version ${version}")
    } else {
        val parseInt = fun(it: String?): Int? {
            if (it != null) {
                return Integer.valueOf(it)
            }
            return null
        }
        val major = parseInt(matched.groups["major"]?.value)
        val minor = parseInt(matched.groups["minor"]?.value)
        val patch = parseInt(matched.groups["patch"]?.value)
        val qualifier = matched.groups["qualifier"]?.value
        println("Major=${major},Minor=${minor},Patch=${patch},Qualifier=${qualifier}")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "me.itdog.rssthis.RssThisApplicationKt"
        )
    }

    eachFile {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        if (this.sourceName.endsWith(".html")
            || this.sourceName.endsWith(".txt")) {
            //println("${this.sourceName} -> ${this.file.absolutePath}")
        }
    }

    from(
        configurations.compileClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )

    archiveFileName.set("rssthis.jar")

    exclude("META-INF/*.RSA", "META-INF/*.SF","META-INF/*.DSA")
}
