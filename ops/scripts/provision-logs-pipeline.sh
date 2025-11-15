#!/usr/bin/env bash
set -euo pipefail

# Provision OpenSearch index template and Kafka Connect sink for service logs
# Requires: docker-compose stack up for zookeeper, kafka-service, opensearch, kafka-connect
# Ports: OpenSearch 9200, Kafka Connect REST mapped to 8085 (container 8083)

OS_URL="http://localhost:9200"
CONNECT_URL="http://localhost:8085"
TEMPLATE_NAME="service-logs-template"
CONNECTOR_NAME="service-logs-opensearch"
TEMPLATE_FILE="ops/opensearch/index-templates/service-logs-template.json"
CONNECTOR_FILE="ops/connectors/service-logs-opensearch.json"

wait_for() {
  local url="$1" name="$2" max_retries="${3:-60}"
  echo "Waiting for ${name} at ${url} ..."
  for i in $(seq 1 "$max_retries"); do
    if curl -sS "$url" >/dev/null; then
      echo "${name} is up"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: ${name} not reachable at ${url}" >&2
  exit 1
}

# 1) Wait for services
wait_for "${OS_URL}" "OpenSearch"
wait_for "${CONNECT_URL}/connector-plugins" "Kafka Connect"

# 2) Verify OpenSearch sink connector plugin is available (Aiven)
if ! curl -sS "${CONNECT_URL}/connector-plugins" | grep -q 'OpensearchSinkConnector'; then
  echo "ERROR: OpenSearch sink plugin (io.aiven.kafka.connect.opensearch.OpensearchSinkConnector) not found in Kafka Connect."
  echo "- Install the plugin under ops/plugins and restart kafka-connect."
  echo "  Download from GitHub Releases: https://github.com/aiven/opensearch-connector-for-apache-kafka/releases/latest"
  echo "  Then run: docker-compose restart kafka-connect"
  exit 1
fi

# 3) Put index template
curl -sS -X PUT "${OS_URL}/_index_template/${TEMPLATE_NAME}" \
  -H 'Content-Type: application/json' \
  --data-binary @"${TEMPLATE_FILE}" | jq . || true

echo "Applied OpenSearch index template: ${TEMPLATE_NAME}"

# 4) Create or update connector
EXISTS=$(curl -sS -o /dev/null -w "%{http_code}" "${CONNECT_URL}/connectors/${CONNECTOR_NAME}")
if [[ "$EXISTS" == "200" ]]; then
  echo "Connector exists; updating ${CONNECTOR_NAME}"
  # PUT expects only the config object, not the wrapper with name
  jq '.config' "${CONNECTOR_FILE}" | \
    curl -sS -X PUT "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/config" \
      -H 'Content-Type: application/json' \
      --data-binary @- | jq . || true
else
  echo "Creating connector ${CONNECTOR_NAME}"
  curl -sS -X POST "${CONNECT_URL}/connectors" \
    -H 'Content-Type: application/json' \
    --data-binary @"${CONNECTOR_FILE}" | jq . || true
fi

# 5) Show status
curl -sS "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status" | jq . || true

echo "Provisioning complete."
