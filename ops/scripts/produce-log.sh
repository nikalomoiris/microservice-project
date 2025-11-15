#!/usr/bin/env bash
set -euo pipefail

# Produce a single structured log message to Kafka topic service-logs via the kafka-service container

TOPIC="service-logs"
BROKER="kafka-service:9092"
CONTAINER="kafka-service"
SMOKE_ID="smoke-$(date +%s%3N)"
SERVICE_NAME="smoke-tester"

# Create topic if missing
if ! docker exec -i "$CONTAINER" kafka-topics --bootstrap-server "$BROKER" --list | grep -q "^${TOPIC}$"; then
  echo "Creating topic ${TOPIC}"
  docker exec -i "$CONTAINER" kafka-topics --bootstrap-server "$BROKER" --create --if-not-exists --topic "$TOPIC" --partitions 1 --replication-factor 1
fi

read -r -d '' MSG << JSON || true
{
  "@timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "level": "INFO",
  "service": "${SERVICE_NAME}",
  "message": "smoke test log",
  "logger": "ops.smoke",
  "thread": "main",
  "traceId": "${SMOKE_ID}",
  "spanId": "${SMOKE_ID}",
  "metadata": {
    "smokeId": "${SMOKE_ID}",
    "env": "local"
  }
}
JSON

# Produce one compact JSON record as a single line, then print only smokeId
{ printf "%s" "$MSG" | tr -d '\n'; echo; } | \
  docker exec -i "$CONTAINER" kafka-console-producer --bootstrap-server "$BROKER" --topic "$TOPIC" >/dev/null
printf "%s\n" "$SMOKE_ID"
