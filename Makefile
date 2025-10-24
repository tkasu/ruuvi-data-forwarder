.PHONY: help build build-assembly test lint format clean run console test-console-sink test-jsonlines-sink test-sinks

ASSEMBLY_JAR := target/scala-3.5.2/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
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
	@echo "  make test-console-sink   - Test Console sink (stdout)"
	@echo "  make test-jsonlines-sink - Test JSON Lines sink (file output)"
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

# Integration test targets
test-console-sink: check-assembly
	@echo "Testing Console sink (stdout)..."
	@echo "Sending test data through Console sink:"
	@head -n 1 $(TEST_DATA) | java -jar $(ASSEMBLY_JAR) 2>&1 | grep -v "^20" | head -5 || true
	@echo "✓ Console sink test complete"

test-jsonlines-sink: check-assembly
	@echo "Testing JSON Lines sink..."
	@rm -f data/telemetry.jsonl
	@echo "Writing test data to data/telemetry.jsonl..."
	@(cat $(TEST_DATA) | RUUVI_SINK_TYPE=jsonlines java -jar $(ASSEMBLY_JAR) > /dev/null 2>&1 &) ; \
		PID=$$! ; \
		sleep 2 ; \
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

test-sinks: test-console-sink test-jsonlines-sink
	@echo ""
	@echo "====================================="
	@echo "✓ All sink tests passed successfully"
	@echo "====================================="
