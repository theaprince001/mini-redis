# MiniRedis

A Redis-compatible in-memory data store built to demonstrate real
distributed systems engineering. Built with Java 21, Netty, Spring Boot,
and React.

## Status

Week 1: RESP2 protocol, Netty server, `PING`/`ECHO`/`QUIT`, backpressure.

## Requirements

- JDK 21
- Gradle 8 (wrapper will be added)
- `redis-cli` for manual smoke tests

## Build and run

```bash
./gradlew build
./gradlew :redis-server:run
```

Then:

```bash
redis-cli -p 6379 PING
```

## Design doc

See `docs/design.md` (v0.3).