import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.4.0"
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.bitaspire"
version = "1.1.3"

repositories {
    mavenLocal()

    flatDir { dirs("libraries") }

    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.maven.apache.org/maven2/")
    maven("https://jitpack.io")
    maven("https://croabeast.github.io/repo/")
}

dependencies {
    // JetBrains
    compileOnly("org.jetbrains:annotations:26.0.2")
    annotationProcessor("org.jetbrains:annotations:26.0.2")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")

    // Spigot API
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")

    implementation(files("libraries/CyberCore-2.0.0.jar"))

    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files("libraries/RivalHarvesterHoesAPI.jar"))
    compileOnly(files("libraries/RivalPickaxesAPI.jar"))

    compileOnly("com.zaxxer:HikariCP:7.0.2")
    compileOnly("com.mysql:mysql-connector-j:9.5.0")
    compileOnly("org.xerial:sqlite-jdbc:3.51.1.0")
    compileOnly("org.postgresql:postgresql:42.7.8")

    implementation("me.croabeast.expr4j:core:1.0")
    implementation("me.croabeast.expr4j:big-decimal:1.0")
    implementation("me.croabeast.expr4j:double:1.0")

    compileOnly("ch.obermuhlner:big-math:2.3.2")
    compileOnly("org.apache.commons:commons-lang3:3.18.0")
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false

    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        encoding = "UTF-8"
        charSet = "UTF-8"
        docEncoding = "UTF-8"

        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_9))
            addBooleanOption("html5", true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-Xlint:-options")
    options.compilerArgs.add("-Xlint:-deprecation")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    exclude(
        "META-INF/**",
        "org/apache/commons/**",
        "org/intellij/**",
        "org/jetbrains/**",
        "me/croabeast/file/plugin/YAMLPlugin.*"
    )
}
