plugins {
    `java-library`
    `maven-publish`
}

base {
    archivesName.set("jooxsource-main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.jar {
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
}

dependencies {
    compileOnly("dev.arbjerg:lavaplayer:2.2.2")
    compileOnly("org.slf4j:slf4j-api:2.0.12")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")

    testImplementation("dev.arbjerg:lavaplayer:2.2.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.slf4j:slf4j-simple:2.0.12")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = base.archivesName.get()
        }
    }
}
