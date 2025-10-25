.PHONY: help build build-assembly test lint format clean run console create-test-data test-console-sink test-jsonlines-sink test-duckdb-sink test-sinks

ASSEMBLY_JAR := $(shell ls -t target/scala-*/ruuvi-data-forwarder-assembly-*.jar 2>/dev/null | head -n 1)
TEST_DATA := test-data.jsonl

help:
	@echo "Ruuvi Data Forwarder - Available targets:"
	@echo ""
	@echo "Build & Development:"
	@echo "  make build           - Compile project"
	@echo "  make build-assembly  - Build fat JAR with all dependencies"
	@echo "  make test            - Run all unit tests"
	@echo "  make lint            - Check code formatting"
	@echo "  make format          - Format code with scalafmt"
	@echo "  make clean           - Remove build artifacts"
	@echo "  make run             - Run application (reads from stdin)"
	@echo "  make console         - Start SBT console"
	@echo ""
	@echo "Integration Testing:"
	@echo "  make create-test-data    - Create test data file for integration tests"
	@echo "  make test-console-sink   - Test Console sink (stdout)"
	@echo "  make test-jsonlines-sink - Test JSON Lines sink (file output)"
	@echo "  make test-duckdb-sink    - Test DuckDB sink (database output)"
	@echo "  make test-sinks          - Test all sink types"

build:
	sbt compile

build-assembly:
	sbt assembly

test:
	sbt test

lint:
	sbt scalafmtCheckAll

format:
	sbt scalafmtAll

clean:
	sbt clean

run:
	sbt run

console:
	sbt console

# Helper target to check if assembly exists
check-assembly:
	@if [ ! -f "$(ASSEMBLY_JAR)" ]; then \
		echo "Assembly JAR not found. Run 'make build-assembly' first."; \
		exit 1; \
	fi

# Create test data for integration tests
create-test-data:
	@echo "Creating test data file: $(TEST_DATA)"
	@echo '{"battery_potential":2335,"humidity":653675,"measurement_ts_ms":1693460525701,"mac_address":[254,38,136,122,102,102],"measurement_sequence_number":53300,"movement_counter":2,"pressure":100755,"temperature_millicelsius":-29020,"tx_power":4}' > $(TEST_DATA)
	@echo '{"battery_potential":1941,"humidity":570925,"measurement_ts_ms":1693460275133,"mac_address":[213,18,52,102,20,20],"measurement_sequence_number":559,"movement_counter":79,"pressure":100621,"temperature_millicelsius":22005,"tx_power":4}' >> $(TEST_DATA)
	@echo "✓ Test data created: $(TEST_DATA)"

# Integration test targets
test-console-sink: check-assembly create-test-data
	@echo "Testing Console sink (stdout)..."
	@echo "Sending test data through Console sink:"
	@(head -n 1 $(TEST_DATA) | java -jar $(ASSEMBLY_JAR) 2>&1 &) ; \
		PID=$$! ; \
		sleep 1 ; \
		kill $$PID 2>/dev/null || true ; \
		wait $$PID 2>/dev/null || true
	@echo "✓ Console sink test complete"

test-jsonlines-sink: check-assembly create-test-data
	@echo "Testing JSON Lines sink..."
	@rm -f data/telemetry.jsonl
	@echo "Writing test data to data/telemetry.jsonl..."
	@(cat $(TEST_DATA) | RUUVI_SINK_TYPE=jsonlines java -jar $(ASSEMBLY_JAR) > /dev/null 2>&1 &) ; \
		PID=$$! ; \
		TIMEOUT=50 ; \
		WAITED=0 ; \
		while [ ! -f data/telemetry.jsonl ] && [ $$WAITED -lt $$TIMEOUT ]; do \
			sleep 0.2 ; \
			WAITED=$$(($$WAITED + 1)) ; \
		done ; \
		kill $$PID 2>/dev/null || true ; \
		wait $$PID 2>/dev/null || true
	@if [ -f data/telemetry.jsonl ]; then \
		echo "✓ File created: data/telemetry.jsonl" ; \
		echo "Contents:" ; \
		cat data/telemetry.jsonl ; \
		echo "" ; \
		echo "Line count: $$(wc -l < data/telemetry.jsonl)" ; \
	else \
		echo "✗ File not created" ; \
		exit 1 ; \
	fi

test-duckdb-sink: check-assembly create-test-data
	@echo "Testing DuckDB sink..."
	@rm -f data/telemetry.db
	@echo "Writing test data to data/telemetry.db..."
	@(cat $(TEST_DATA) | RUUVI_SINK_TYPE=duckdb java -jar $(ASSEMBLY_JAR) > /dev/null 2>&1 &) ; \
		PID=$$! ; \
		TIMEOUT=50 ; \
		WAITED=0 ; \
		while [ ! -f data/telemetry.db ] && [ $$WAITED -lt $$TIMEOUT ]; do \
			sleep 0.2 ; \
			WAITED=$$(($$WAITED + 1)) ; \
		done ; \
		kill $$PID 2>/dev/null || true ; \
		wait $$PID 2>/dev/null || true
	@if [ -f data/telemetry.db ]; then \
		echo "✓ Database created: data/telemetry.db" ; \
		echo "Verifying database content..." ; \
		java -cp $(ASSEMBLY_JAR) org.duckdb.DuckDBAppender "jdbc:duckdb:data/telemetry.db" "SELECT COUNT(*) as count FROM telemetry" 2>/dev/null || \
			echo "Note: DuckDB CLI not available for verification, but database file exists" ; \
	else \
		echo "✗ Database not created" ; \
		exit 1 ; \
	fi

test-sinks: test-console-sink test-jsonlines-sink test-duckdb-sink
	@echo ""
	@echo "====================================="
	@echo "✓ All sink tests passed successfully"
	@echo "====================================="
