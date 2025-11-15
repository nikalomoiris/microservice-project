# Observability Ops — OpenSearch + Kafka Connect

This folder contains local dev configs to persist structured logs from Kafka (`service-logs` topic) into OpenSearch with dashboards.

## Components

- OpenSearch (`opensearch`:9200) and OpenSearch Dashboards (`opensearch-dashboards`:5601)
- Kafka Connect (`kafka-connect`:8083 REST) with the Elasticsearch/OpenSearch sink connector

## One-time setup (local dev)

1) Start the stack:

```bash
docker-compose up -d opensearch opensearch-dashboards kafka-connect
```

2) Install the OpenSearch sink connector plugin (only once per machine). The Connect image loads plugins from `./ops/plugins`:

- Recommended: Aiven OpenSearch Sink Connector
  - Download binaries (zip/tar) from GitHub Releases: https://github.com/aiven/opensearch-connector-for-apache-kafka/releases/latest
  - Unpack so the plugin directory exists under: `ops/plugins/opensearch-connector-for-apache-kafka/`
  - The connector class is `io.aiven.kafka.connect.opensearch.OpensearchSinkConnector`
- Alternative: Confluent Elasticsearch Sink may not be compatible with OpenSearch; prefer the Aiven plugin.

3) Create index template in OpenSearch:

```bash
curl -X PUT "http://localhost:9200/_index_template/service-logs-template" \
  -H 'Content-Type: application/json' \
  --data-binary @ops/opensearch/index-templates/service-logs-template.json
```

4) Create Kafka Connect sink for topic `service-logs`:

```bash
curl -X POST http://localhost:8085/connectors \
  -H 'Content-Type: application/json' \
  --data-binary @ops/connectors/service-logs-opensearch.json
```

Notes:
- Kafka Connect REST is mapped to `localhost:8085` (container port 8083). Adjust if changed.
- The connector config routes the Kafka topic `service-logs` to the OpenSearch index `service-logs`.
- For production, configure ISM/ILM (index lifecycle) and authentication (security disabled here for local dev only).
 - After installing the plugin, restart Kafka Connect: `docker-compose restart kafka-connect`.

## Dashboards

- Open `http://localhost:5601`, create a data view for index pattern `service-logs*` with `@timestamp` as the time field.

## Troubleshooting

- Verify plugin availability: `GET http://localhost:8085/connector-plugins` (look for `OpensearchSinkConnector`)
- Check connector status: `GET http://localhost:8085/connectors/service-logs-opensearch/status`
- Check OpenSearch health: `GET http://localhost:9200/_cat/indices?v`

## Quick Provisioning and Smoke Test

Provision both the OpenSearch template and Kafka Connect sink, then run a smoke test end-to-end:

```bash
chmod +x ops/scripts/*.sh

# Provision template + connector (requires plugin installed)
ops/scripts/provision-logs-pipeline.sh

# Produce a sample log and verify it lands in OpenSearch
ops/scripts/smoke-logs.sh
```
