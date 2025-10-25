## Ruuvi Data Forwarder

A Scala 3 + ZIO-based middleware for processing and forwarding Ruuvi Tag sensor telemetry data. Reads newline-delimited JSON from stdin and forwards to configurable sinks.

**Status:** Active Development

### Features

- ✅ **Multiple Sinks**: Console (stdout), JSON Lines (file), and DuckDB (database) sinks
- ✅ **Configuration**: Type-safe config with HOCON + environment variable overrides
- ✅ **Structured Logging**: ZIO logging with SLF4J/Logback backend
- ✅ **Stream Processing**: ZIO Streams with backpressure support
- ✅ **Type Safety**: Compile-time JSON validation and derivation
- 🚧 **Future**: HTTP, S3, PostgreSQL, Kafka sinks (planned)

### Requirements

- JDK 11+ (tested with JDK 25)
- SBT (Scala Build Tool)

### Quick Start

```shell
# Build the project
make build-assembly

# Test with sample data (Console sink)
echo '{"battery_potential":2335,"humidity":653675,"measurement_ts_ms":1693460525701,"mac_address":[254,38,136,122,102,102],"measurement_sequence_number":53300,"movement_counter":2,"pressure":100755,"temperature_millicelsius":-29020,"tx_power":4}' | java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# Test JSON Lines sink
make test-jsonlines-sink

# Test DuckDB sink
make test-duckdb-sink
```

### Available Sinks

#### 1. Console Sink (Default)

Writes JSON to stdout. Useful for piping to other tools or for debugging.

```shell
# Via make
make run

# Or directly
java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

#### 2. JSON Lines Sink

Writes newline-delimited JSON to a file with automatic directory creation and append support.

**Features:**
- Creates parent directories automatically
- Appends to existing files (doesn't overwrite)
- Debug logging of each telemetry record (configurable)

**Configuration:**
```shell
# Set via environment variables
export RUUVI_SINK_TYPE=jsonlines
export RUUVI_JSONLINES_PATH=data/telemetry.jsonl  # Optional, default shown
export RUUVI_JSONLINES_DEBUG_LOGGING=true          # Optional, default shown

# Run
java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

Or inline:
```shell
RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

#### 3. DuckDB Sink

Writes telemetry to a DuckDB database file with automatic table creation and schema management.

**Features:**
- Creates database file and table automatically
- Creates parent directories automatically
- Appends to existing database (doesn't overwrite)
- Supports both file-based and in-memory databases
- Debug logging of each telemetry record (configurable)
- High-performance columnar storage optimized for analytics

**Configuration:**
```shell
# Set via environment variables
export RUUVI_SINK_TYPE=duckdb
export RUUVI_DUCKDB_PATH=data/telemetry.db        # Optional, default shown
export RUUVI_DUCKDB_TABLE_NAME=telemetry          # Optional, default shown
export RUUVI_DUCKDB_DEBUG_LOGGING=true            # Optional, default shown

# Run
java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

Or inline:
```shell
RUUVI_SINK_TYPE=duckdb java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**For in-memory database:**
```shell
RUUVI_SINK_TYPE=duckdb RUUVI_DUCKDB_PATH=":memory:" java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**Querying the database:**
```shell
# Install DuckDB CLI (https://duckdb.org/docs/installation/)
duckdb data/telemetry.db

# Example queries
SELECT COUNT(*) FROM telemetry;
SELECT * FROM telemetry ORDER BY measurement_ts_ms DESC LIMIT 10;
SELECT AVG(temperature_millicelsius / 1000.0) as avg_temp_celsius FROM telemetry;

# Query by MAC address (stored in standard format: FE:26:88:7A:66:66)
SELECT * FROM telemetry WHERE mac_address = 'D5:12:34:66:14:14';
```

**Database Schema:**
- MAC addresses are stored in standard hex format (e.g., `FE:26:88:7A:66:66`)
- Timestamps are stored as milliseconds since epoch
- All sensor values stored as integers for precision

### Configuration

Configuration is loaded from `src/main/resources/application.conf` with environment variable overrides.

**Default Configuration:**
```hocon
sink {
    sink-type = "console"              # "console", "jsonlines", or "duckdb"

    json-lines {
      path = "data/telemetry.jsonl"    # Output file path
      debug-logging = true              # Log each telemetry to debug level
    }

    duckdb {
      path = "data/telemetry.db"       # Database file path (use ":memory:" for in-memory)
      table-name = "telemetry"         # Table name for storing telemetry
      debug-logging = true              # Log each telemetry to debug level
    }
  }
```

**Environment Variables:**
- `RUUVI_SINK_TYPE` - Sink type: `console`, `jsonlines`, or `duckdb`
- `RUUVI_JSONLINES_PATH` - Output file path for JSON Lines sink
- `RUUVI_JSONLINES_DEBUG_LOGGING` - Enable/disable debug logging (`true`/`false`)
- `RUUVI_DUCKDB_PATH` - Database file path for DuckDB sink (use `:memory:` for in-memory)
- `RUUVI_DUCKDB_TABLE_NAME` - Table name for DuckDB sink
- `RUUVI_DUCKDB_DEBUG_LOGGING` - Enable/disable debug logging (`true`/`false`)

### Development

#### Build Commands

All commands are available via Make:

```shell
# Show all available commands
make help

# Compile project
make build

# Build fat JAR with dependencies
make build-assembly

# Run unit tests
make test

# Check code formatting
make lint

# Auto-format code
make format

# Clean build artifacts
make clean

# Run application (reads from stdin)
make run

# Start SBT console
make console
```

#### Testing

**Unit Tests:**
```shell
make test
```

**Integration Tests:**
```shell
# Test Console sink (stdout)
make test-console-sink

# Test JSON Lines sink (file output)
make test-jsonlines-sink

# Test DuckDB sink (database output)
make test-duckdb-sink

# Test all sinks
make test-sinks
```

### Usage Examples

#### With ruuvi-reader-rs

```shell
# Console output
ruuvi-reader-rs | java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# Save to file
ruuvi-reader-rs | RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
# Data saved to: data/telemetry.jsonl

# Save to DuckDB database
ruuvi-reader-rs | RUUVI_SINK_TYPE=duckdb java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
# Data saved to: data/telemetry.db
```

#### From File

```shell
# Console sink
cat telemetry.jsonl | java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# JSON Lines sink with custom path
cat telemetry.jsonl | \
  RUUVI_SINK_TYPE=jsonlines \
  RUUVI_JSONLINES_PATH=/var/log/ruuvi/output.jsonl \
  java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# DuckDB sink with custom path and table
cat telemetry.jsonl | \
  RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_PATH=/var/data/sensor.db \
  RUUVI_DUCKDB_TABLE_NAME=ruuvi_data \
  java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

#### Fan-out to Multiple Sinks

```shell
# Using tee to write to multiple sinks simultaneously
ruuvi-reader-rs | tee \
  >(RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar) \
  >(RUUVI_SINK_TYPE=duckdb java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar) \
  >(java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar)
```

### Data Format

**Input/Output (newline-delimited JSON):**
```json
{
  "battery_potential": 2335,
  "humidity": 653675,
  "measurement_ts_ms": 1693460525701,
  "mac_address": [254, 38, 136, 122, 102, 102],
  "measurement_sequence_number": 53300,
  "movement_counter": 2,
  "pressure": 100755,
  "temperature_millicelsius": -29020,
  "tx_power": 4
}
```

### Project Structure

```
ruuvi-data-forwarder/
├── src/main/scala/
│   ├── App.scala                           # Main application
│   ├── config/                             # Configuration models
│   │   ├── AppConfig.scala
│   │   └── SinkConfig.scala
│   ├── dto/
│   │   └── RuuviTelemetry.scala           # Data models
│   ├── sinks/                              # Sink implementations
│   │   ├── SensorValuesSink.scala         # Sink trait
│   │   ├── ConsoleSensorValuesSink.scala  # Console sink
│   │   └── JsonLinesSensorValuesSink.scala # JSON Lines sink
│   └── sources/                            # Source implementations
│       ├── SensorValuesSource.scala
│       └── ConsoleSensorValuesSource.scala
├── src/main/resources/
│   ├── application.conf                    # Default configuration
│   └── logback.xml                         # Logging configuration
├── src/test/scala/
│   ├── AppSpec.scala
│   └── sinks/JsonLinesSensorValuesSinkSpec.scala
├── data/                                   # Default output directory
├── build.sbt                               # Build configuration
└── Makefile                                # Build & test commands
```

### Troubleshooting

**JSON Parse Errors:**
```
Error parsing telemetry: ...
```
- Verify input JSON matches the expected schema
- Check for malformed JSON (use `jq` to validate)

**OutOfMemoryError:**
```shell
java -Xmx2G -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**File Permission Errors (JSON Lines sink):**
- Ensure write permissions for output directory
- Check parent directory creation (done automatically)

### Related Projects

- [ruuvi-reader-rs](https://github.com/tkasu/ruuvi-reader-rs) - Upstream BLE scanner
- [ruuvitag-api](https://github.com/tkasu/ruuvitag-api) - Downstream REST API (planned)

### License

(No license specified)