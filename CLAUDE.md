# ruuvi-data-forwarder

## Overview

**ruuvi-data-forwarder** is a Scala 3 application built with ZIO that acts as middleware in the Ruuvitag telemetry pipeline. It processes sensor data from various sources and forwards it to configurable targets (HTTP endpoints, S3, databases, etc.). Currently implements stdin-to-stdout forwarding with JSON validation.

**Status:** Work in Progress (WIP)

**Language:** Scala 3.3.0

**Framework:** ZIO 2.0.13 (Functional Effect System)

## Purpose

This utility serves as the data processing and routing layer by:
- Reading sensor telemetry from configurable sources (currently stdin)
- Parsing and validating JSON data into type-safe models
- Transforming and enriching data as needed
- Forwarding data to multiple sinks (currently stdout, future: HTTP, S3, DB)
- Providing robust error handling and stream processing capabilities

## Architecture

### Tech Stack

- **Effect System:** ZIO 2.0.13 (functional effects, async, resource management)
- **Streams:** ZIO Streams 2.0.13 (reactive stream processing)
- **JSON:** ZIO-JSON 0.6.1 (compile-time JSON codec derivation)
- **Build Tool:** SBT 1.9.4
- **Testing:** ZIO-Test 2.0.13
- **Plugins:** sbt-assembly (fat JAR), sbt-scalafmt (code formatting)

### Project Structure

```
ruuvi-data-forwarder/
├── src/
│   ├── main/scala/
│   │   ├── App.scala                           # Main application entry (24 lines)
│   │   ├── dto/
│   │   │   └── RuuviTelemetry.scala            # Data model (22 lines)
│   │   ├── sources/
│   │   │   ├── SensorValuesSource.scala        # Source trait (7 lines)
│   │   │   ├── ConsoleSensorValuesSource.scala # Stdin implementation (18 lines)
│   │   │   └── SourceError.scala               # Error ADT (11 lines)
│   │   └── sinks/
│   │       ├── SensorValuesSink.scala          # Sink trait (7 lines)
│   │       └── ConsoleSensorValuesSink.scala   # Stdout implementation (10 lines)
│   └── test/scala/
│       └── AppSpec.scala                       # Integration test (64 lines)
├── build.sbt                                    # Build configuration
├── project/
│   ├── build.properties                         # SBT version 1.9.4
│   └── plugins.sbt                              # sbt-assembly, sbt-scalafmt
├── .scalafmt.conf                               # Scala 3 formatting rules
└── README.md                                    # Usage documentation
```

### Key Components

**App.scala:24** - Main application
- `forwarder()` - Core pipeline that:
  1. Takes a source stream and a sink
  2. Pipes telemetry data through the sink
  3. Catches `RuuviParseError` and logs errors
  4. Runs forever until stream completes

- `run()` - Application entry point:
  1. Prints "Reading StdIn"
  2. Creates forwarder with console source and sink
  3. Handles graceful shutdown on stream completion
  4. Logs all parse errors

**dto/RuuviTelemetry.scala:22** - Data model
- Case class representing Ruuvi sensor telemetry
- Uses `@jsonMemberNames(SnakeCase)` for snake_case JSON mapping
- Fields: temperatureMillicelsius, humidity, pressure, batteryPotential, txPower, movementCounter, measurementSequenceNumber, measurementTsMs, macAddress
- Auto-derived ZIO JSON codec via `DeriveJsonCodec.gen`

**sources/** - Input abstractions
- `SensorValuesSource` - Trait providing `ZStream[Any, SourceError, RuuviTelemetry]`
- `ConsoleSensorValuesSource` - Reads newline-delimited JSON from stdin
- `SourceError` - ADT with `RuuviParseError(msg, cause)` and `StreamShutdown`

**sinks/** - Output abstractions
- `SensorValuesSink` - Trait providing `ZSink[Any, Any, RuuviTelemetry, Nothing, Unit]`
- `ConsoleSensorValuesSink` - Writes JSON to stdout with trailing newline

### Design Patterns

**Functional Effects:** All side effects wrapped in ZIO for:
- Type-safe error handling
- Resource management
- Testability
- Composability

**Tagless Final:** Source and Sink traits allow multiple implementations without changing pipeline logic

**Streaming:** ZIO Streams for efficient, backpressure-aware data flow

## Data Format

### Input (from stdin)

Newline-delimited JSON matching ruuvi-reader-rs output:

```json
{
  "mac_address": [213, 18, 52, 102, 20, 20],
  "humidity": 570925,
  "temperature_millicelsius": 22005,
  "pressure": 100621,
  "battery_potential": 1941,
  "tx_power": 4,
  "movement_counter": 79,
  "measurement_sequence_number": 559,
  "measurement_ts_ms": 1693460275133
}
```

### Output (to stdout)

Same JSON format with snake_case field names (validated and re-serialized):

```json
{
  "temperature_millicelsius": 22080,
  "humidity": 576425,
  "pressure": 100556,
  "battery_potential": 2176,
  "tx_power": 4,
  "movement_counter": 79,
  "measurement_sequence_number": 1589,
  "measurement_ts_ms": 1693460525701,
  "mac_address": [213, 18, 52, 102, 20, 20]
}
```

## Building and Running

### Prerequisites

1. **Java Development Kit (JDK)**
   - Tested with JDK 20
   - Any JDK 11+ should work
   ```bash
   java -version
   ```

2. **SBT (Scala Build Tool)**
   ```bash
   # macOS
   brew install sbt

   # Linux
   echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
   curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
   sudo apt-get update
   sudo apt-get install sbt
   ```

### Build Commands

This project includes a Makefile with standard targets. Run `make help` to see all available commands.

```bash
# Using Make (recommended)
make build           # Compile project
make build-assembly  # Build fat JAR with all dependencies
make test            # Run all tests
make lint            # Check code formatting
make format          # Format code with scalafmt
make clean           # Remove build artifacts
make run             # Run application (reads from stdin)
make console         # Start SBT console

# Or use SBT directly
sbt compile
sbt assembly
sbt test
sbt scalafmtCheckAll
sbt scalafmtAll
```

**Build Output:**
- Assembly JAR: `target/scala-3.3.0/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar`

### Running

```bash
# With test data (echo single line)
echo '{"battery_potential":2335,"humidity":653675,"measurement_ts_ms":1693460525701,"mac_address":[254,38,136,122,102,102],"measurement_sequence_number":53300,"movement_counter":2,"pressure":100755,"temperature_millicelsius":-29020,"tx_power":4}' | java -jar target/scala-3.3.0/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With ruuvi-reader-rs (live data)
../ruuvi-reader-rs/target/release/ruuvi-reader-rs | java -jar target/scala-3.3.0/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With file input
cat test-data.jsonl | java -jar target/scala-3.3.0/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# Redirect output
../ruuvi-reader-rs/target/release/ruuvi-reader-rs | java -jar target/scala-3.3.0/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar > processed-data.jsonl
```

### Testing

```bash
# Using Make (recommended)
make test            # Run all tests
make lint            # Check code formatting

# Or use SBT directly
sbt test
sbt scalafmtCheckAll test

# Continuous testing (re-run on file changes)
sbt ~test
```

**Test Coverage:**
- ✅ 1 integration test passing
- `AppSpec` - Verifies stdin to stdout pipeline
- Tests pass-through of valid JSON telemetry
- Verifies JSON parsing and serialization

## Configuration

**Current State:** No configuration files - hardcoded to stdin/stdout

**Future Configuration (Planned):**
```hocon
# application.conf (Typesafe Config / HOCON)
ruuvi-data-forwarder {
  sources = [
    {
      type = "console"
      format = "json"
    }
  ]

  sinks = [
    {
      type = "http"
      url = "https://api.example.com/telemetry"
      batch-size = 100
      timeout = "30s"
    },
    {
      type = "s3"
      bucket = "ruuvi-telemetry"
      prefix = "raw/"
    }
  ]
}
```

## Integration

### Upstream: ruuvi-reader-rs

```bash
ruuvi-reader-rs | java -jar ruuvi-data-forwarder-assembly.jar
```

Receives newline-delimited JSON from BLE scanner.

### Downstream: ruuvi-api (Planned)

```bash
ruuvi-reader-rs | \
  java -jar ruuvi-data-forwarder-assembly.jar | \
  <insert to database or post to API>
```

Could forward to HTTP API, write to database, or save to object storage.

### Parallel Processing (Future)

```bash
# Tee to multiple sinks
ruuvi-reader-rs | tee >(java -jar forwarder.jar --sink=http) \
                     >(java -jar forwarder.jar --sink=s3) \
                     >(java -jar forwarder.jar --sink=postgres)
```

## Development

### Code Style

Project uses **Scalafmt** with Scala 3 formatting:

```bash
# Check formatting
sbt scalafmtCheckAll

# Auto-format code
sbt scalafmtAll
```

Key formatting rules (`.scalafmt.conf`):
- Scala 3 dialect
- 2-space indentation
- 120 character line width

### Git History

- `8aa8cc9` - Support for measurement_ts_ms field
- `2a01d73` - Add parsing to case class, improved error handling
- `777c9fd` - Update README, code formatting
- `b5e5c9d` - Add structure and tests
- `e149202` - Implement line reader echo

### Adding New Sources

1. Create new class implementing `SensorValuesSource` trait
2. Return `ZStream[Any, SourceError, RuuviTelemetry]`
3. Example for HTTP polling:

```scala
class HttpSensorValuesSource(url: String) extends SensorValuesSource {
  override def stream: ZStream[Any, SourceError, RuuviTelemetry] =
    ZStream
      .repeatZIO(fetchFromHttp(url))
      .mapError(e => RuuviParseError(e.getMessage, e))

  private def fetchFromHttp(url: String): Task[RuuviTelemetry] = ???
}
```

### Adding New Sinks

1. Create new class implementing `SensorValuesSink` trait
2. Return `ZSink[Any, Any, RuuviTelemetry, Nothing, Unit]`
3. Example for HTTP POST:

```scala
class HttpSensorValuesSink(apiUrl: String) extends SensorValuesSink {
  override def sink: ZSink[Any, Any, RuuviTelemetry, Nothing, Unit] =
    ZSink.foreach { telemetry =>
      postToApi(apiUrl, telemetry).orDie
    }

  private def postToApi(url: String, data: RuuviTelemetry): Task[Unit] = ???
}
```

## Dependencies

Key dependencies from build.sbt:

| Dependency | Version | Purpose |
|------------|---------|---------|
| zio | 2.0.13 | Effect system and async runtime |
| zio-streams | 2.0.13 | Reactive stream processing |
| zio-json | 0.6.1 | JSON codec derivation |
| zio-test | 2.0.13 | Testing framework |
| sbt-assembly | 2.1.1 | Build fat JARs |
| sbt-scalafmt | 2.5.0 | Code formatting |

## Troubleshooting

**Issue: JSON parsing errors**
- Verify input JSON matches expected schema
- Check for invalid UTF-8 or malformed JSON
- Look for errors logged to stderr

**Issue: OutOfMemoryError with large streams**
- Increase JVM heap: `java -Xmx2G -jar forwarder.jar`
- ZIO Streams should handle backpressure automatically

**Issue: Tests fail on formatting**
```bash
# Auto-fix formatting issues
sbt scalafmtAll
sbt scalafmtCheckAll test
```

**Issue: Assembly JAR not found**
```bash
# Ensure assembly completed
sbt clean assembly
ls -lh target/scala-3.3.0/*assembly*.jar
```

## Future Enhancements

**Planned Features:**
- HTTP sink for posting to REST APIs
- S3 sink for cloud storage
- PostgreSQL/TimescaleDB sink for time-series storage
- Kafka sink for event streaming
- Configuration file support (Typesafe Config)
- Multiple simultaneous sinks with routing rules
- Data transformation pipeline (filtering, aggregation)
- Metrics and monitoring (Prometheus)
- Graceful shutdown with SIGTERM handling
- Rate limiting and backpressure configuration

**Potential Improvements:**
- Batch processing for higher throughput
- Compression for network sinks
- Retry logic with exponential backoff
- Dead letter queue for failed records
- Schema evolution and versioning

## Related Projects

- **ruuvi-reader-rs** - Upstream BLE scanner that feeds this forwarder
- **ruuvi-api** - Downstream REST API for serving telemetry

## Resources

- [ZIO Documentation](https://zio.dev/)
- [ZIO Streams Guide](https://zio.dev/reference/stream/)
- [ZIO JSON](https://github.com/zio/zio-json)
- [Scala 3 Book](https://docs.scala-lang.org/scala3/book/introduction.html)
