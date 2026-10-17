plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.gradleup.shadow")
    application
}

// The playlist, DSP and format logic is shared with the Android app.
sourceSets {
    main { kotlin.srcDir("../app/src/main/java/com/illyaua/winamp/core") }
    test { kotlin.srcDir("../app/src/test/java/com/illyaua/winamp/core") }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

dependencies {
    // Java Sound service providers: MP3, Ogg Vorbis and FLAC. WAV and MIDI are built into the JDK.
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("org.jflac:jflac-codec:1.5.2")

    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.illyaua.winamp.desktop.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("winamp-classic")
    archiveClassifier.set("")
    archiveVersion.set("")
    // Each sound codec registers itself through META-INF/services; those files must be merged, not overwritten.
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.illyaua.winamp.desktop.MainKt"
    }
}
