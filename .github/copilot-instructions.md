# GitHub Copilot Instructions for ruuvi-data-forwarder

## Project Overview

This is a Scala 3 application using ZIO (functional effect system) that processes Ruuvi sensor telemetry data. It acts as middleware in the Ruuvitag telemetry pipeline, reading sensor data from various sources and forwarding it to configurable targets.

**Status:** Active Development  
**Language:** Scala 3.5.2  
**Framework:** ZIO 2.1.14

## Architecture & Tech Stack

- **Effect System:** ZIO 2.1.14 for functional effects, async operations, and resource management
- **Streams:** ZIO Streams 2.1.14 for reactive stream processing with backpressure
- **JSON:** ZIO-JSON 0.7.3 for compile-time JSON codec derivation
- **Configuration:** ZIO Config 4.0.2 with Typesafe Config (HOCON format)
- **Logging:** ZIO Logging 2.3.2 with SLF4J2 backend + Logback 1.5.6
- **Build Tool:** SBT 1.9.4
- **Testing:** ZIO-Test 2.1.14
- **Code Formatting:** Scalafmt 3.x (Scala 3 dialect, 2-space indentation, 120 char line width)

## Key Design Patterns

1. **Functional Effects:** All side effects are wrapped in ZIO for type-safe error handling, resource management, and composability
2. **Tagless Final:** Source and Sink traits allow multiple implementations without changing pipeline logic
3. **Streaming:** ZIO Streams for efficient, backpressure-aware data flow

## Project Structure

```
src/main/scala/
├── App.scala                           # Main application entry point
├── config/                             # Configuration models
│   ├── AppConfig.scala                 # Main app configuration
│   └── SinkConfig.scala                # Sink configuration
├── dto/
│   └── RuuviTelemetry.scala           # Data model with JSON codecs
├── sources/
│   ├── SensorValuesSource.scala       # Source trait
│   ├── ConsoleSensorValuesSource.scala # Stdin implementation
│   └── SourceError.scala              # Error ADT
└── sinks/
    ├── SensorValuesSink.scala         # Sink trait
    ├── ConsoleSensorValuesSink.scala  # Stdout implementation
    └── JsonLinesSensorValuesSink.scala # JSON Lines file sink

src/test/scala/
├── AppSpec.scala                      # Integration tests
└── sinks/
    └── JsonLinesSensorValuesSinkSpec.scala # Unit tests
```

## Build & Test Commands

### Prerequisites
- JDK 11+ (tested with JDK 20)
- SBT 1.9.4

### Using Makefile (recommended)
```bash
make build           # Compile project
make build-assembly  # Build fat JAR with all dependencies
make test            # Run all tests
make lint            # Check code formatting with scalafmt
make format          # Auto-format code with scalafmt
make clean           # Remove build artifacts
make run             # Run application (reads from stdin)
make console         # Start SBT console
```

### Using SBT directly
```bash
sbt compile
sbt assembly
sbt test
sbt scalafmtCheckAll
sbt scalafmtAll
```

## Code Style & Conventions

### Formatting Rules (enforced by Scalafmt)
- Scala 3 dialect
- 2-space indentation
- 120 character line width
- Snake case for JSON field names (via `@jsonMemberNames(SnakeCase)`)

### Before Committing
**ALWAYS** run formatting check and tests:
```bash
sbt scalafmtCheckAll test
```

or using make:
```bash
make lint && make test
```

### Naming Conventions
- **Case classes:** PascalCase (e.g., `RuuviTelemetry`, `AppConfig`)
- **Traits:** PascalCase (e.g., `SensorValuesSource`, `SensorValuesSink`)
- **Methods/fields:** camelCase (e.g., `temperatureMillicelsius`, `forwarder()`)
- **JSON fields:** snake_case (automatically handled by ZIO-JSON with `@jsonMemberNames(SnakeCase)`)

## Working with ZIO

### Common Patterns

**Creating ZIO effects:**
```scala
ZIO.succeed(value)              // Pure value
ZIO.fail(error)                 // Failed effect
ZIO.attempt(sideEffect)         // From side-effecting code
```

**Working with streams:**
```scala
ZStream.fromIterable(collection)
  .map(transform)
  .filter(predicate)
  .run(sink)
```

**Error handling:**
```scala
effect
  .catchAll(e => ZIO.succeed(fallback))
  .orDie  // Convert errors to defects
```

**Resource management:**
```scala
ZIO.acquireReleaseWith(acquire)(release)(use)
```

### Testing with ZIO-Test
```scala
test("description") {
  for {
    result <- effect
  } yield assertTrue(result == expected)
}
```

## Data Model

### RuuviTelemetry
The core data model representing sensor readings:
```scala
case class RuuviTelemetry(
  temperatureMillicelsius: Int,
  humidity: Int,
  pressure: Int,
  batteryPotential: Int,
  txPower: Int,
  movementCounter: Int,
  measurementSequenceNumber: Int,
  measurementTsMs: Long,
  macAddress: Chunk[Int]
)
```

**JSON format:** Newline-delimited JSON with snake_case field names

## Configuration

Configuration is loaded from `src/main/resources/application.conf` using ZIO Config with HOCON format. Environment variables can override config values.

**Example:**
```hocon
ruuvi-data-forwarder {
  sink {
    sink-type = "console"
    sink-type = ${?RUUVI_SINK_TYPE}
  }
}
```

**Environment variables:**
- `RUUVI_SINK_TYPE` - Sink type: `console` or `jsonlines`
- `RUUVI_JSONLINES_PATH` - Output file path for JSON Lines sink
- `RUUVI_JSONLINES_DEBUG_LOGGING` - Enable/disable debug logging

## Adding New Features

### Adding a New Source
1. Create class implementing `SensorValuesSource` trait
2. Return `ZStream[Any, SourceError, RuuviTelemetry]`
3. Handle errors by mapping to `SourceError` ADT

### Adding a New Sink
1. Create class implementing `SensorValuesSink` trait
2. Return `ZSink[Any, Any, RuuviTelemetry, Nothing, Unit]`
3. Use `ZSink.foreach` for per-element processing

### Adding Configuration
1. Add case class in `config/` package
2. Use ZIO Config automatic derivation
3. Add HOCON config in `application.conf`
4. Document environment variable overrides

## Testing Guidelines

- Use ZIO-Test framework for all tests
- Test files should mirror source structure
- Test public APIs and important edge cases
- Integration tests in `AppSpec.scala`
- Unit tests for individual components in component-specific test files

**Running tests:**
```bash
sbt test           # Run all tests once
sbt ~test          # Watch mode (re-run on changes)
```

## CI/CD

The project uses GitHub Actions for CI (`.github/workflows/ci.yml`):
- **Lint:** Code formatting check with scalafmt
- **Test:** Run all tests
- **Build:** Build assembly JAR artifact

All jobs must pass for PR approval.

## Common Tasks

### Running the Application
```bash
# Build assembly JAR first
make build-assembly

# With test data
echo '{"battery_potential":2335,...}' | java -jar target/scala-3.5.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With stdin source
make run
```

### Debugging
- Check logs in console output (Logback configured in `src/main/resources/logback.xml`)
- Use ZIO's built-in tracing and logging features
- Enable debug logging for specific components via Logback configuration

## Documentation

The repository has extensive documentation:
- **README.md** - Quick start guide
- **AGENTS.md** - Comprehensive technical documentation for AI agents
- **This file** - Copilot-specific instructions

When making changes, update relevant documentation to keep it in sync with code.

## Important Notes

- **Never remove working test code** - Only add or modify tests as needed
- **Use existing libraries** - Don't add new dependencies unless absolutely necessary
- **Follow ZIO patterns** - Use ZIO idioms for effects, streams, and resource management
- **Format before committing** - Always run `make format` or `sbt scalafmtAll`
- **Test your changes** - Run `make test` before committing
