plugins {
    application
}

dependencies {
    implementation(project(":redis-protocol"))
    implementation(project(":redis-core"))
    implementation("io.netty:netty-transport")
    implementation("io.netty:netty-codec")
    implementation("io.netty:netty-handler")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    testImplementation(testFixtures(project(":redis-core")))
}

application {
    applicationName = "miniredis-server"
    mainClass.set("io.miniredis.server.MiniRedisServer")
    applicationDefaultJvmArgs = listOf(
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
        "-Xms256m",
        "-Xmx2g"
    )
}