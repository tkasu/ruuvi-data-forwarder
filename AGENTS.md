# ruuvi-data-forwarder

## Overview

**ruuvi-data-forwarder** is a Scala 3 application built with ZIO that acts as middleware in the Ruuvitag telemetry pipeline. It processes sensor data from various sources and forwards it to configurable targets. Currently implements Console (stdout), JSON Lines (file), DuckDB (database), DuckLake (lakehouse), and HTTP sinks with full configuration support.

**Status:** Active Development

**Language:** Scala 3.7.3

**Framework:** ZIO 2.1.14 (Functional Effect System)

## Purpose

This utility serves as the data processing and routing layer by:
- Reading sensor telemetry from configurable sources (currently stdin)
- Parsing and validating JSON data into type-safe models
- Transforming and enriching data as needed
- Forwarding data to multiple sinks (Console, JSON Lines, DuckDB, DuckLake, HTTP)
- Providing robust error handling and stream processing capabilities

## Architecture

### Tech Stack

- **Effect System:** ZIO 2.1.14 (functional effects, async, resource management)
- **Streams:** ZIO Streams 2.1.14 (reactive stream processing)
- **JSON:** ZIO-JSON 0.7.3 (compile-time JSON codec derivation)
- **Configuration:** ZIO Config 4.0.2 with Typesafe Config support
- **Logging:** ZIO Logging 2.3.2 with SLF4J2 backend + Logback 1.5.6
- **Build Tool:** SBT 1.10.6
- **Testing:** ZIO-Test 2.1.14
- **Plugins:** sbt-assembly 2.1.1 (fat JAR), sbt-scalafmt 2.5.0 (code formatting)

### Project Structure

```
ruuvi-data-forwarder/
├── src/
│   ├── main/scala/
│   │   ├── App.scala                           # Main application entry
│   │   ├── config/                             # Configuration models
│   │   │   ├── AppConfig.scala                 # Main app config
│   │   │   └── SinkConfig.scala                # Sink configuration
│   │   ├── dto/
│   │   │   └── RuuviTelemetry.scala            # Data model
│   │   ├── sources/
│   │   │   ├── SensorValuesSource.scala        # Source trait
│   │   │   ├── ConsoleSensorValuesSource.scala # Stdin implementation
│   │   │   └── SourceError.scala               # Error ADT
│   │   └── sinks/
│   │       ├── SensorValuesSink.scala          # Sink trait
│   │       ├── ConsoleSensorValuesSink.scala   # Stdout implementation
│   │       ├── JsonLinesSensorValuesSink.scala # JSON Lines file sink
│   │       ├── DuckDBSensorValuesSink.scala    # DuckDB/DuckLake sink
│   │       └── HttpSensorValuesSink.scala      # HTTP API sink
│   ├── main/resources/
│   │   ├── application.conf                     # Configuration (HOCON)
│   │   └── logback.xml                          # Logging configuration
│   └── test/scala/
│       ├── AppSpec.scala                       # Integration test
│       └── sinks/
│           └── JsonLinesSensorValuesSinkSpec.scala # Sink unit tests
├── data/                                        # Default output directory
│   └── .gitkeep                                 # Keep directory in git
├── build.sbt                                    # Build configuration
├── project/
│   ├── build.properties                         # SBT version 1.10.6
│   └── plugins.sbt                              # sbt-assembly, sbt-scalafmt
├── Makefile                                     # Build & test commands
├── .scalafmt.conf                               # Scala 3 formatting rules
├── .gitignore                                   # Git ignore rules
└── README.md                                    # Usage documentation
```

### Key Components

**App.scala** - Main application
- `forwarder()` - Core pipeline that:
  1. Takes a source stream and a sink
  2. Pipes telemetry data through the sink
  3. Catches `RuuviParseError` and logs errors with ZIO logging
  4. Runs forever until stream completes

- `selectSink()` - Sink selection based on configuration:
  1. Reads SinkConfig to determine which sink to use
  2. Creates appropriate sink instance (Console, JsonLines, DuckDB, or HTTP)
  3. Returns configured sink wrapped in ZIO

- `run()` - Application entry point:
  1. Loads configuration from application.conf with ENV overrides
  2. Initializes SLF4J logging backend
  3. Creates forwarder with console source and configured sink
  4. Handles graceful shutdown on stream completion

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
- `SensorValuesSink` - Trait providing `ZSink[Any, Any, Chunk[RuuviTelemetry], Nothing, Unit]`
- `ConsoleSensorValuesSink` - Writes JSON to stdout with trailing newline
- `JsonLinesSensorValuesSink` - Writes JSON Lines to file with:
  - Automatic parent directory creation
  - Append mode (doesn't overwrite existing files)
  - Optional debug logging of each telemetry record
  - Configurable output path
- `DuckDBSensorValuesSink` - Writes to DuckDB database or DuckLake lakehouse with:
  - Two modes: standard DuckDB or DuckLake (lakehouse format)
  - DuckDB mode: Single database file with direct SQL operations
  - DuckLake mode: Catalog database + Parquet data files
  - Support for DuckDB, SQLite, or PostgreSQL catalog databases
  - Automatic table creation and schema management
  - Batch insertion for optimal performance
  - Configurable batch size and latency
  - SQL injection protection via table name validation
- `HttpSensorValuesSink` - Posts telemetry to HTTP API with:
  - Configurable endpoint URL and sensor name
  - Retry logic with exponential backoff
  - Timeout configuration

**config/** - Configuration management
- `AppConfig` - Main application configuration
- `SinkConfig` - Sink-specific configuration with type-safe enums
- `SinkType` - Enum for sink types: Console, JsonLines, DuckDB, Http
- `JsonLinesConfig` - JSON Lines sink configuration (path, debug logging)
- `DuckDBConfig` - DuckDB/DuckLake configuration:
  - Standard DuckDB: path, table-name
  - DuckLake mode: ducklake-enabled flag
  - Catalog type selection (DuckDB, SQLite, PostgreSQL)
  - Separate paths for catalog and data files
- `DuckLakeConfig` - DuckLake-specific settings (catalog-type, catalog-path, data-path)
- `CatalogType` - Enum for catalog database types: DuckDB, SQLite, Postgres
- `HttpConfig` - HTTP sink configuration (api-url, sensor-name, timeout, retries)
- Configuration loaded from `application.conf` with environment variable overrides

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
   - Tested with JDK 25 (LTS)
   - Any JDK 25+ should work
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
- Assembly JAR: `target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar`

### Running

**Console Sink (default):**
```bash
# With test data
echo '{"battery_potential":2335,"humidity":653675,"measurement_ts_ms":1693460525701,"mac_address":[254,38,136,122,102,102],"measurement_sequence_number":53300,"movement_counter":2,"pressure":100755,"temperature_millicelsius":-29020,"tx_power":4}' | java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With ruuvi-reader-rs (live data)
../ruuvi-reader-rs/target/release/ruuvi-reader-rs | java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With file input
cat test-data.jsonl | java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**JSON Lines Sink:**
```bash
# Write to default path (data/telemetry.jsonl)
RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With custom path
RUUVI_SINK_TYPE=jsonlines RUUVI_JSONLINES_PATH=/var/log/ruuvi/data.jsonl \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# Disable debug logging
RUUVI_SINK_TYPE=jsonlines RUUVI_JSONLINES_DEBUG_LOGGING=false \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**DuckDB Sink (Standard Mode):**
```bash
# Write to default DuckDB database (data/telemetry.db)
RUUVI_SINK_TYPE=duckdb java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With custom path and table name
RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_PATH=/var/lib/ruuvi/telemetry.db \
  RUUVI_DUCKDB_TABLE_NAME=sensor_data \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With custom batch settings
RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_DESIRED_BATCH_SIZE=100 \
  RUUVI_DUCKDB_DESIRED_MAX_BATCH_LATENCY_SECONDS=60 \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**DuckLake Sink (Lakehouse Mode):**
```bash
# Write to DuckLake with SQLite catalog (default for local use)
RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With custom catalog and data paths
RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE=sqlite \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH=/var/lib/ruuvi/catalog.sqlite \
  RUUVI_DUCKDB_DUCKLAKE_DATA_PATH=/var/lib/ruuvi/parquet_files/ \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With DuckDB catalog (single client, faster)
RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE=duckdb \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH=data/catalog.ducklake \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# With PostgreSQL catalog (multi-client, distributed)
RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE=postgres \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH="dbname=ruuvi_catalog host=localhost user=postgres" \
  RUUVI_DUCKDB_DUCKLAKE_DATA_PATH=/shared/ruuvi/data/ \
  java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

### Testing

**Unit Tests:**
```bash
# Using Make (recommended)
make test            # Run all unit tests
make lint            # Check code formatting

# Or use SBT directly
sbt test
sbt scalafmtCheckAll test

# Continuous testing (re-run on file changes)
sbt ~test
```

**Integration Tests:**
```bash
# Test Console sink
make test-console-sink

# Test JSON Lines sink
make test-jsonlines-sink

# Test DuckDB sink
make test-duckdb-sink

# Test DuckLake sink
make test-ducklake-sink

# Test all sinks
make test-sinks
```

**Data Management:**
```bash
# Remove all data files (DB, JSON, DuckLake)
make clean-data

# Remove only integration test data
make clean-test-data

# Remove only DuckLake catalog and data files
make clean-ducklake-data
```

**Test Coverage:**
- ✅ 10 unit tests passing (as of DuckLake implementation)
- `AppSpec` - Verifies stdin to stdout pipeline
- `JsonLinesSensorValuesSinkSpec` - Tests JSON Lines sink:
  - Write telemetry to file
  - Create parent directories automatically
  - Append to existing files
- `DuckDBSensorValuesSinkSpec` - Tests DuckDB sink:
  - Write telemetry to in-memory and file databases
  - Create database and table automatically
  - Append to existing database
  - Batch insertion
  - Table name validation (SQL injection protection)
  - DuckLake mode with DuckDB catalog
  - DuckLake append operations
- All tests validate JSON parsing and serialization

## Configuration

**Implementation:** Type-safe configuration using ZIO Config with HOCON format

Configuration is loaded from `src/main/resources/application.conf` with environment variable overrides.

**Current Configuration:**
```hocon
# application.conf
sink {
    # Sink type: "console", "jsonlines", "duckdb", or "http"
    sink-type = "console"
    sink-type = ${?RUUVI_SINK_TYPE}

    json-lines {
      path = "data/telemetry.jsonl"
      path = ${?RUUVI_JSONLINES_PATH}
      debug-logging = true
      debug-logging = ${?RUUVI_JSONLINES_DEBUG_LOGGING}
    }

    duckdb {
      path = "data/telemetry.db"
      path = ${?RUUVI_DUCKDB_PATH}
      table-name = "telemetry"
      table-name = ${?RUUVI_DUCKDB_TABLE_NAME}
      debug-logging = true
      debug-logging = ${?RUUVI_DUCKDB_DEBUG_LOGGING}
      desired-batch-size = 5
      desired-batch-size = ${?RUUVI_DUCKDB_DESIRED_BATCH_SIZE}
      desired-max-batch-latency-seconds = 30
      desired-max-batch-latency-seconds = ${?RUUVI_DUCKDB_DESIRED_MAX_BATCH_LATENCY_SECONDS}

      # DuckLake mode (lakehouse format)
      ducklake-enabled = false
      ducklake-enabled = ${?RUUVI_DUCKDB_DUCKLAKE_ENABLED}

      ducklake {
        catalog-type = "sqlite"  # "duckdb", "sqlite", or "postgres"
        catalog-type = ${?RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE}
        catalog-path = "data/catalog.sqlite"
        catalog-path = ${?RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH}
        data-path = "data/ducklake_files/"
        data-path = ${?RUUVI_DUCKDB_DUCKLAKE_DATA_PATH}
      }
    }
}
```

**Environment Variables:**

Console & JSON Lines:
- `RUUVI_SINK_TYPE` - Sink type: `console`, `jsonlines`, `duckdb`, or `http`
- `RUUVI_JSONLINES_PATH` - Output file path for JSON Lines sink
- `RUUVI_JSONLINES_DEBUG_LOGGING` - Enable/disable debug logging (`true`/`false`)

DuckDB (Standard Mode):
- `RUUVI_DUCKDB_PATH` - Path to DuckDB database file
- `RUUVI_DUCKDB_TABLE_NAME` - Table name for storing telemetry
- `RUUVI_DUCKDB_DEBUG_LOGGING` - Enable/disable debug logging
- `RUUVI_DUCKDB_DESIRED_BATCH_SIZE` - Number of records per batch
- `RUUVI_DUCKDB_DESIRED_MAX_BATCH_LATENCY_SECONDS` - Max seconds before flushing batch

DuckLake (Lakehouse Mode):
- `RUUVI_DUCKDB_DUCKLAKE_ENABLED` - Enable DuckLake mode (`true`/`false`)
- `RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE` - Catalog database: `duckdb`, `sqlite`, or `postgres`
- `RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH` - Path to catalog database file (or connection string for PostgreSQL)
- `RUUVI_DUCKDB_DUCKLAKE_DATA_PATH` - Directory for Parquet data files

**Configuration Models:**
- `AppConfig` - Main application configuration
- `SinkConfig` - Sink configuration with type-safe enum (`SinkType`)
- `SinkType` - Enum: Console, JsonLines, DuckDB, Http
- `JsonLinesConfig` - JSON Lines sink configuration
- `DuckDBConfig` - DuckDB/DuckLake configuration
- `DuckLakeConfig` - DuckLake-specific settings
- `CatalogType` - Enum: DuckDB, SQLite, Postgres
- `HttpConfig` - HTTP sink configuration

**DuckLake Catalog Database Options:**
- **DuckDB**: Single-client, file-based. Best for local single-process usage.
- **SQLite**: Multi-client with retry logic. Best for local multi-process usage.
- **PostgreSQL**: Fully distributed. Best for multi-user/multi-host environments.

**Future Sinks (Planned):**
- S3 sink for cloud storage
- Kafka sink for event streaming

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
ls -lh target/scala-3.*/*assembly*.jar
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

## Contributing

### Working with AI Assistants

When committing changes that were created with the help of an AI assistant (e.g., GitHub Copilot, Gemini, Claude), please use the `Co-authored-by:` trailer in your commit message to give proper credit. This helps track the origin of the code and acknowledges the role of the AI in the development process.

**Example:**

```
feat: Add user authentication

Implement the user login and registration endpoints.

Co-authored-by: Name of the AI <ai-assistant@example.com>
```

## Resources

- [ZIO Documentation](https://zio.dev/)
- [ZIO Streams Guide](https://zio.dev/reference/stream/)
- [ZIO JSON](https://github.com/zio/zio-json)
- [Scala 3 Book](https://docs.scala-lang.org/scala3/book/introduction.html)