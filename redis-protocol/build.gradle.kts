plugins {
    `java-library`
}

dependencies {
    api(platform("io.netty:netty-bom:4.1.138.Final"))
    api("io.netty:netty-buffer")
}