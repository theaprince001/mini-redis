plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":redis-protocol"))
    api(platform("io.netty:netty-bom:4.1.138.Final"))
    api("io.netty:netty-buffer")
}