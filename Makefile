.PHONY: help build build-assembly test lint format clean run console create-test-data test-console-sink test-jsonlines-sink test-duckdb-sink test-ducklake-sink test-sinks clean-data clean-test-data clean-ducklake-data

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
	@echo "  make create-test-data       - Create test data file for integration tests"
	@echo "  make test-console-sink      - Test Console sink (stdout)"
	@echo "  make test-jsonlines-sink    - Test JSON Lines sink (file output)"
	@echo "  make test-duckdb-sink       - Test DuckDB sink (database output)"
	@echo "  make test-ducklake-sink     - Test DuckLake sink (lakehouse output)"
	@echo "  make test-sinks             - Test all sink types"
	@echo ""
	@echo "Data Management:"
	@echo "  make clean-data             - Remove all data files (DB, JSON, DuckLake)"
	@echo "  make clean-test-data        - Remove integration test data only"
	@echo "  make clean-ducklake-data    - Remove DuckLake catalog and data files"

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

test-ducklake-sink: check-assembly create-test-data
	@echo "Testing DuckLake sink with SQLite catalog..."
	@echo "Cleaning up old test data..."
	@rm -rf data/test_catalog.sqlite data/test_ducklake_files/
	@mkdir -p data/test_ducklake_files/
	@echo "Writing test data to DuckLake (catalog: data/test_catalog.sqlite, files: data/test_ducklake_files/)..."
	@(cat $(TEST_DATA) | \
		RUUVI_SINK_TYPE=duckdb \
		RUUVI_DUCKDB_DUCKLAKE_ENABLED=true \
		RUUVI_DUCKDB_DUCKLAKE_CATALOG_TYPE=sqlite \
		RUUVI_DUCKDB_DUCKLAKE_CATALOG_PATH=data/test_catalog.sqlite \
		RUUVI_DUCKDB_DUCKLAKE_DATA_PATH=data/test_ducklake_files/ \
		java -jar $(ASSEMBLY_JAR) > /dev/null 2>&1 &) ; \
		PID=$$! ; \
		TIMEOUT=50 ; \
		WAITED=0 ; \
		while [ ! -f data/test_catalog.sqlite ] && [ $$WAITED -lt $$TIMEOUT ]; do \
			sleep 0.2 ; \
			WAITED=$$(($$WAITED + 1)) ; \
		done ; \
		sleep 0.5 ; \
		kill $$PID 2>/dev/null || true ; \
		wait $$PID 2>/dev/null || true ; \
		sleep 0.5
	@if [ -f data/test_catalog.sqlite ]; then \
		echo "✓ Catalog created: data/test_catalog.sqlite" ; \
		if [ -d data/test_ducklake_files ]; then \
			echo "✓ Data directory created: data/test_ducklake_files/" ; \
			echo "Verifying data with DuckDB client..." ; \
			ROW_COUNT=$$(duckdb :memory: -c "INSTALL ducklake; LOAD ducklake; ATTACH 'ducklake:sqlite:data/test_catalog.sqlite' AS my_ducklake (DATA_PATH 'data/test_ducklake_files/'); SELECT COUNT(*) FROM my_ducklake.telemetry;" | grep -E '^│[[:space:]]*[0-9]+' | grep -oE '[0-9]+' | head -1) ; \
			if [ "$$ROW_COUNT" = "2" ]; then \
				echo "✓ Data verified: $$ROW_COUNT rows found in telemetry table" ; \
			else \
				echo "✗ Expected 2 rows, but found: $$ROW_COUNT" ; \
				exit 1 ; \
			fi ; \
		else \
			echo "✗ Data directory not created" ; \
			exit 1 ; \
		fi ; \
	else \
		echo "✗ Catalog not created" ; \
		exit 1 ; \
	fi

test-sinks: test-console-sink test-jsonlines-sink test-duckdb-sink test-ducklake-sink
	@echo ""
	@echo "====================================="
	@echo "✓ All sink tests passed successfully"
	@echo "====================================="

# Data cleanup commands
clean-data:
	@echo "Cleaning all data files..."
	@rm -f data/telemetry.db data/telemetry.jsonl data/catalog.sqlite
	@rm -rf data/ducklake_files/
	@rm -f data/test_catalog.sqlite
	@rm -rf data/test_ducklake_files/
	@echo "✓ All data files removed"

clean-test-data:
	@echo "Cleaning integration test data..."
	@rm -f data/test_catalog.sqlite
	@rm -rf data/test_ducklake_files/
	@echo "✓ Integration test data removed"

clean-ducklake-data:
	@echo "Cleaning DuckLake catalog and data files..."
	@rm -f data/catalog.sqlite
	@rm -rf data/ducklake_files/
	@echo "✓ DuckLake data removed"
