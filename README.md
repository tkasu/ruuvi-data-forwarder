## Ruuvi Data Forwarder

A Scala 3 + ZIO-based middleware for processing and forwarding Ruuvi Tag sensor telemetry data. Reads newline-delimited JSON from stdin and forwards to configurable sinks.

**Status:** Active Development

### Features

- ✅ **Multiple Sinks**: Console (stdout), JSON Lines (file), and HTTP (ruuvitag-api) sinks
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
echo '{"battery_potential":2335,"humidity":653675,"measurement_ts_ms":1693460525701,"mac_address":[254,38,136,122,102,102],"measurement_sequence_number":53300,"movement_counter":2,"pressure":100755,"temperature_millicelsius":-29020,"tx_power":4}' | java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

# Test JSON Lines sink
make test-jsonlines-sink
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

#### 3. HTTP Sink

Sends telemetry data to a ruuvitag-api compatible HTTP endpoint. Each RuuviTelemetry record is transformed into multiple measurements (temperature, humidity, pressure, battery, etc.) and posted to the API.

**Features:**
- Transforms Ruuvi telemetry to ruuvitag-api format
- Posts each measurement type separately
- Automatic retry on failure (via ZIO HTTP)
- Debug logging of each HTTP request (configurable)

**Configuration:**
```shell
# Set via environment variables
export RUUVI_SINK_TYPE=http
export RUUVI_HTTP_API_URL=http://localhost:8080/v1  # Optional, default shown
export RUUVI_HTTP_SENSOR_NAME=default-sensor        # Optional, default shown
export RUUVI_HTTP_DEBUG_LOGGING=true                # Optional, default shown

# Run
java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

Or inline:
```shell
RUUVI_SINK_TYPE=http RUUVI_HTTP_API_URL=http://localhost:8080/v1 RUUVI_HTTP_SENSOR_NAME=my-sensor java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

**API Format:**

The sink posts to `/telemetry/{sensorName}` with payload:
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
  // ... (pressure, battery, tx_power, movement_counter, measurement_sequence_number)
]
```

### Configuration

Configuration is loaded from `src/main/resources/application.conf` with environment variable overrides.

**Default Configuration:**
```hocon
sink {
    sink-type = "console"              # "console", "jsonlines", or "http"

    json-lines {
      path = "data/telemetry.jsonl"    # Output file path
      debug-logging = true              # Log each telemetry to debug level
    }

    http {
      api-url = "http://localhost:8080/v1"  # ruuvitag-api base URL
      sensor-name = "default-sensor"        # Sensor name for API requests
      debug-logging = true                  # Log each HTTP request to debug level
    }
  }
```

**Environment Variables:**
- `RUUVI_SINK_TYPE` - Sink type: `console`, `jsonlines`, or `http`
- `RUUVI_JSONLINES_PATH` - Output file path for JSON Lines sink
- `RUUVI_JSONLINES_DEBUG_LOGGING` - Enable/disable debug logging (`true`/`false`)
- `RUUVI_HTTP_API_URL` - Base URL for ruuvitag-api HTTP sink
- `RUUVI_HTTP_SENSOR_NAME` - Sensor name for HTTP sink API requests
- `RUUVI_HTTP_DEBUG_LOGGING` - Enable/disable debug logging for HTTP sink (`true`/`false`)

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

# Send to HTTP API
ruuvi-reader-rs | RUUVI_SINK_TYPE=http RUUVI_HTTP_API_URL=http://api.example.com/v1 RUUVI_HTTP_SENSOR_NAME=outdoor-sensor java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
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

# HTTP sink with custom API
cat telemetry.jsonl | \
  RUUVI_SINK_TYPE=http \
  RUUVI_HTTP_API_URL=http://api.example.com/v1 \
  RUUVI_HTTP_SENSOR_NAME=my-sensor \
  java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

#### Fan-out to Multiple Sinks

```shell
# Using tee to write to multiple sinks simultaneously
ruuvi-reader-rs | tee \
  >(RUUVI_SINK_TYPE=jsonlines java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar) \
  >(RUUVI_SINK_TYPE=http RUUVI_HTTP_API_URL=http://api.example.com/v1 java -jar target/scala-3.6.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar) \
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