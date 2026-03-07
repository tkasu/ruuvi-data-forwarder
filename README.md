## Ruuvi Data Forwarder

A Scala 3 + ZIO-based middleware for processing and forwarding Ruuvi Tag sensor telemetry data. Reads newline-delimited JSON from stdin and forwards to configurable sinks.

**Status:** Active Development

### Features

- ✅ **Multiple Sinks**: Console (stdout), JSON Lines (file), DuckDB (database), DuckLake (lakehouse), and HTTP (ruuvitag-api) sinks
- ✅ **Configuration**: Type-safe config with HOCON + environment variable overrides
- ✅ **Structured Logging**: ZIO logging with SLF4J/Logback backend
- ✅ **Stream Processing**: ZIO Streams with backpressure support
- ✅ **Type Safety**: Compile-time JSON validation and derivation
- 🚧 **Future**: S3, PostgreSQL, Kafka sinks (planned)

### Requirements

- JDK 11+ (tested with JDK 25)
- SBT (Scala Build Tool)

### Quick Start

```shell
# Build the project
make build-assembly

# Test with sample data (Console sink)
echo '{"battery_potential":2335,"humidity":653675,"measurement_ts_ms":1693460525701,"mac_address":[254,38,136,122,102,102],"measurement_sequence_number":53300,"movement_counter":2,"pressure":100755,"temperature_millicelsius":-29020,"tx_power":4}' | java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# Test JSON Lines sink
make test-jsonlines-sink

# Test DuckDB sink
make test-duckdb-sink

# Test DuckLake sink
make test-ducklake-sink
```

### Available Sinks

#### 1. Console Sink (Default)

Writes JSON to stdout. Useful for piping to other tools or for debugging.

```shell
# Via make
make run

# Or directly
java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
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
java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

Or inline:
```shell
RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
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
java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

Or inline:
```shell
RUUVI_SINK_TYPE=duckdb java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**For in-memory database:**
```shell
RUUVI_SINK_TYPE=duckdb RUUVI_DUCKDB_PATH=":memory:" java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
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

#### 3b. DuckLake Sink (Lakehouse Mode)

An extension of the DuckDB sink that writes telemetry as Parquet files managed by a DuckLake catalog. Suitable for multi-client or distributed scenarios where concurrent readers and writers are needed.

**Features:**
- Stores data as Parquet files for efficient columnar analytics
- Supports three catalog backends: DuckDB (single-client), SQLite (multi-client), PostgreSQL (distributed)
- Creates catalog and data directories automatically
- Appends to existing data (doesn't overwrite)
- Inherits batch size and latency settings from DuckDB sink configuration

**Configuration:**
```shell
# Minimal: enable DuckLake with default SQLite catalog
export RUUVI_SINK_TYPE=duckdb
export RUUVI_DUCKDB_DUCKLAKE_ENABLED=true

# Full options
export RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE=sqlite        # "duckdb", "sqlite", or "postgres"
export RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH=data/ruuvidb.sqlite  # catalog file (or PG connection string)
export RUUVI_DUCKDB_DUCKLAKE_DATA_PATH=data/ducklake_files/    # directory for Parquet data files

# Run
java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**Catalog type comparison:**

| Catalog type | Use case |
|---|---|
| `duckdb` | Single process, fastest local writes |
| `sqlite` | Multiple local processes (default) |
| `postgres` | Multi-host / distributed environments |

**Inline examples:**
```shell
# SQLite catalog (default)
RUUVI_SINK_TYPE=duckdb RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# DuckDB catalog
RUUVI_SINK_TYPE=duckdb RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE=duckdb \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH=data/catalog.ducklake \
  java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# PostgreSQL catalog
RUUVI_SINK_TYPE=duckdb RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE=postgres \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH="dbname=ruuvi_catalog host=localhost user=postgres" \
  RUUVI_DUCKDB_DUCKLAKE_DATA_PATH=/shared/ruuvi/data/ \
  java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

#### 4. HTTP Sink

Sends telemetry data to a ruuvitag-api compatible HTTP endpoint. Each RuuviTelemetry record is transformed into multiple measurements (temperature, humidity, pressure, battery, etc.) and posted to the API.

**Features:**
- Transforms Ruuvi telemetry to ruuvitag-api format
- Posts each measurement type separately
- Automatic retry with exponential backoff (configurable)
- Request timeout protection (configurable)
- Error recovery to prevent stream crashes
- Debug logging of each HTTP request (configurable)
- MAC address validation
- URL encoding for sensor names

**Configuration:**
```shell
# Set via environment variables
export RUUVI_SINK_TYPE=http
export RUUVI_HTTP_API_URL=http://localhost:8081  # Optional, default shown
export RUUVI_HTTP_SENSOR_NAME=default-sensor        # Optional, default shown
export RUUVI_HTTP_DEBUG_LOGGING=true                # Optional, default shown
export RUUVI_HTTP_TIMEOUT_SECONDS=30                # Optional, default shown
export RUUVI_HTTP_MAX_RETRIES=3                     # Optional, default shown

# Run
java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

Or inline:
```shell
RUUVI_SINK_TYPE=http RUUVI_HTTP_API_URL=http://localhost:8081 RUUVI_HTTP_SENSOR_NAME=my-sensor java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**API Format:**

The sink posts to `/telemetry/{sensorName}` with payload (truncated example showing 2 of 7 measurement types):
```json
[
  {
    "telemetry_type": "temperature",
    "data": [
      {
        "sensor_name": "FE:26:88:7A:66:66",
        "timestamp": 1693460525699,
        "value": -29.02
      }
    ]
  },
  {
    "telemetry_type": "humidity",
    "data": [
      {
        "sensor_name": "FE:26:88:7A:66:66",
        "timestamp": 1693460525699,
        "value": 65.3675
      }
    ]
  }
]
```

Each telemetry record generates 7 separate measurement types: `temperature`, `humidity`, `pressure`, `battery`, `tx_power`, `movement_counter`, and `measurement_sequence_number`.

### Configuration

Configuration is loaded from `src/main/resources/application.conf` with environment variable overrides.

**Default Configuration:**
```hocon
sink {
    sink-type = "console"              # "console", "jsonlines", "duckdb", or "http"

    json-lines {
      path = "data/telemetry.jsonl"    # Output file path
      debug-logging = true              # Log each telemetry to debug level
    }

    duckdb {
      path = "data/telemetry.db"       # Database file path (use ":memory:" for in-memory)
      table-name = "telemetry"         # Table name for storing telemetry
      debug-logging = true              # Log each telemetry to debug level
      desired-batch-size = 5           # Records per batch before flush
      desired-max-batch-latency-seconds = 30  # Max seconds before flushing batch

      # DuckLake mode (lakehouse format using Parquet files)
      ducklake-enabled = false

      ducklake {
        catalog-type = "sqlite"        # "duckdb", "sqlite", or "postgres"
        catalog-path = "data/ruuvidb.sqlite"   # catalog file or PG connection string
        data-path = "data/ducklake_files/"     # directory for Parquet data files
      }
    }

    http {
      api-url = "http://localhost:8081"  # ruuvitag-api base URL
      sensor-name = "default-sensor"        # Sensor name for API requests
      debug-logging = true                  # Log each HTTP request to debug level
      timeout-seconds = 30                  # HTTP request timeout in seconds
      max-retries = 3                       # Maximum retry attempts for failed requests
    }
  }
```

**Environment Variables:**
- `RUUVI_SINK_TYPE` - Sink type: `console`, `jsonlines`, `duckdb`, or `http`
- `RUUVI_JSONLINES_PATH` - Output file path for JSON Lines sink
- `RUUVI_JSONLINES_DEBUG_LOGGING` - Enable/disable debug logging (`true`/`false`)
- `RUUVI_DUCKDB_PATH` - Database file path for DuckDB sink (use `:memory:` for in-memory)
- `RUUVI_DUCKDB_TABLE_NAME` - Table name for DuckDB sink
- `RUUVI_DUCKDB_DEBUG_LOGGING` - Enable/disable debug logging (`true`/`false`)
- `RUUVI_DUCKDB_DESIRED_BATCH_SIZE` - Number of records per batch before flush
- `RUUVI_DUCKDB_DESIRED_MAX_BATCH_LATENCY_SECONDS` - Max seconds before flushing a batch
- `RUUVI_DUCKDB_DUCKLAKE_ENABLED` - Enable DuckLake (lakehouse) mode (`true`/`false`)
- `RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE` - Catalog backend: `duckdb`, `sqlite`, or `postgres`
- `RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH` - Path to catalog file, or PostgreSQL connection string
- `RUUVI_DUCKDB_DUCKLAKE_DATA_PATH` - Directory for Parquet data files
- `RUUVI_HTTP_API_URL` - Base URL for ruuvitag-api HTTP sink
- `RUUVI_HTTP_SENSOR_NAME` - Sensor name for HTTP sink API requests
- `RUUVI_HTTP_DEBUG_LOGGING` - Enable/disable debug logging for HTTP sink (`true`/`false`)
- `RUUVI_HTTP_TIMEOUT_SECONDS` - HTTP request timeout in seconds (default: 30)
- `RUUVI_HTTP_MAX_RETRIES` - Maximum retry attempts for failed HTTP requests (default: 3)

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

# Test DuckLake sink (lakehouse output)
make test-ducklake-sink

# Test all sinks
make test-sinks
```

### Usage Examples

#### With ruuvi-reader-rs

```shell
# Console output
ruuvi-reader-rs | java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# Save to file
ruuvi-reader-rs | RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
# Data saved to: data/telemetry.jsonl

# Save to DuckDB database
ruuvi-reader-rs | RUUVI_SINK_TYPE=duckdb java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
# Data saved to: data/telemetry.db

# Save to DuckLake (lakehouse, SQLite catalog by default)
ruuvi-reader-rs | RUUVI_SINK_TYPE=duckdb RUUVI_DUCKDB_DUCKLAKE_ENABLED=true java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
# Catalog: data/ruuvidb.sqlite  Data: data/ducklake_files/

# Send to HTTP API
ruuvi-reader-rs | RUUVI_SINK_TYPE=http RUUVI_HTTP_API_URL=http://api.example.com RUUVI_HTTP_SENSOR_NAME=outdoor-sensor java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

#### From File

```shell
# Console sink
cat telemetry.jsonl | java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# JSON Lines sink with custom path
cat telemetry.jsonl | \
  RUUVI_SINK_TYPE=jsonlines \
  RUUVI_JSONLINES_PATH=/var/log/ruuvi/output.jsonl \
  java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# DuckDB sink with custom path and table
cat telemetry.jsonl | \
  RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_PATH=/var/data/sensor.db \
  RUUVI_DUCKDB_TABLE_NAME=ruuvi_data \
  java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# DuckLake sink with custom catalog and data paths
cat telemetry.jsonl | \
  RUUVI_SINK_TYPE=duckdb \
  RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
  RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH=/var/data/catalog.sqlite \
  RUUVI_DUCKDB_DUCKLAKE_DATA_PATH=/var/data/ducklake_files/ \
  java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# HTTP sink with custom API
cat telemetry.jsonl | \
  RUUVI_SINK_TYPE=http \
  RUUVI_HTTP_API_URL=http://api.example.com \
  RUUVI_HTTP_SENSOR_NAME=my-sensor \
  java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

#### Fan-out to Multiple Sinks

```shell
# Using tee to write to multiple sinks simultaneously
ruuvi-reader-rs | tee \
  >(RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar) \
  >(RUUVI_SINK_TYPE=duckdb java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar) \
  >(RUUVI_SINK_TYPE=http RUUVI_HTTP_API_URL=http://api.example.com java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar) \
  >(java -jar target/scala-3.7.3/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar)
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
│   │   ├── JsonLinesSensorValuesSink.scala # JSON Lines sink
│   │   ├── DuckDBSensorValuesSink.scala   # DuckDB / DuckLake sink
│   │   └── HttpSensorValuesSink.scala     # HTTP sink
│   └── sources/                            # Source implementations
│       ├── SensorValuesSource.scala
│       └── ConsoleSensorValuesSource.scala
├── src/main/resources/
│   ├── application.conf                    # Default configuration
│   └── logback.xml                         # Logging configuration
├── src/test/scala/
│   ├── AppSpec.scala
│   └── sinks/
│       ├── JsonLinesSensorValuesSinkSpec.scala
│       └── DuckDBSensorValuesSinkSpec.scala
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

**File Permission Errors (JSON Lines sink):**
- Ensure write permissions for output directory
- Check parent directory creation (done automatically)

### Related Projects

- [ruuvi-reader-rs](https://github.com/tkasu/ruuvi-reader-rs) - Upstream BLE scanner
- [ruuvitag-api](https://github.com/tkasu/ruuvitag-api) - Downstream REST API (planned)

### License

(No license specified)