#!/usr/bin/env bash
set -euo pipefail

# Generate a burst of structured logs into Kafka topic service-logs
# Usage: ./generate-logs.sh [count]

COUNT=${1:-50}
TOPIC="service-logs"
BROKER="kafka-service:9092"
CONTAINER="kafka-service"

# Ensure topic exists
if ! docker exec -i "$CONTAINER" kafka-topics --bootstrap-server "$BROKER" --list | grep -q "^${TOPIC}$"; then
  echo "Creating topic ${TOPIC}"
  docker exec -i "$CONTAINER" kafka-topics --bootstrap-server "$BROKER" --create --if-not-exists --topic "$TOPIC" --partitions 1 --replication-factor 1
fi

services=("order-service" "inventory-service" "product-service" "review-service" "logging-service")
levels=("INFO" "DEBUG" "WARN" "ERROR")

# Produce COUNT messages in a single producer session
{
  for ((i=1; i<=COUNT; i++)); do
    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    svc=${services[$RANDOM % ${#services[@]}]}
    lvl=${levels[$RANDOM % ${#levels[@]}]}
    id="bulk-$(date +%s%3N)-$i"
    msg=$(cat << JSON
{
  "@timestamp": "${ts}",
  "timestamp": "${ts}",
  "level": "${lvl}",
  "service": "${svc}",
  "message": "bulk generated log #${i}",
  "logger": "ops.bulk",
  "thread": "main",
  "traceId": "${id}",
  "spanId": "${id}",
  "metadata": {
    "generator": "generate-logs.sh",
    "iteration": ${i},
    "env": "local"
  }
}
JSON
)
    printf "%s\n" "${msg}" | tr -d '\n'
    echo
  done
} | docker exec -i "$CONTAINER" kafka-console-producer --bootstrap-server "$BROKER" --topic "$TOPIC" >/dev/null

echo "Emitted ${COUNT} logs to topic ${TOPIC}."
