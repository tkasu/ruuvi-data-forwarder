.PHONY: help build build-assembly test lint format clean run console

ASSEMBLY_JAR := target/scala-3.3.0/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar

help:
	@echo "Ruuvi Data Forwarder - Available targets:"
	@echo "  make build          - Compile project"
	@echo "  make build-assembly - Build fat JAR with all dependencies"
	@echo "  make test           - Run all tests"
	@echo "  make lint           - Check code formatting"
	@echo "  make format         - Format code with scalafmt"
	@echo "  make clean          - Remove build artifacts"
	@echo "  make run            - Run application (reads from stdin)"
	@echo "  make console        - Start SBT console"

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
