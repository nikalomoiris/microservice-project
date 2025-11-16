# Observability Ops — OpenSearch, Kafka Connect, and Zipkin

This folder contains local dev configs for the complete observability stack:
- Structured logs: Kafka → OpenSearch (via Kafka Connect)
- Distributed traces: Zipkin → OpenSearch (persistent storage)
- Visualization: OpenSearch Dashboards with pre-built dashboards

## Components

- OpenSearch (`opensearch`:9200) and OpenSearch Dashboards (`opensearch-dashboards`:5601)
- Kafka Connect (`kafka-connect`:8083 REST) with the Elasticsearch/OpenSearch sink connector
- Zipkin (`zipkin-service`:9411) with OpenSearch backend for trace persistence

## Quick Start

1) Start the stack:

```bash
docker-compose up -d
```

2) Provision observability infrastructure:

```bash
# Provision log pipeline
./ops/scripts/provision-logs-pipeline.sh

# Provision tracing
./ops/scripts/provision-tracing.sh
```

3) Import pre-built dashboards:

```bash
# Import saved searches and data views
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "osd-xsrf: true" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@./ops/opensearch/saved_objects/zipkin-saved-objects.ndjson"

# Import main tracing dashboard
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "osd-xsrf: true" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@./ops/opensearch/saved_objects/tracing-dashboard.ndjson"

# Import advanced metrics dashboard
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "osd-xsrf: true" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@./ops/opensearch/saved_objects/tracing-advanced.ndjson"
```

4) Generate sample data:

```bash
# Generate traces
./scripts/test-distributed-tracing.sh

# Generate logs
./ops/scripts/generate-logs.sh
```

5) Access UIs:
- Zipkin: http://localhost:9411
- OpenSearch Dashboards: http://localhost:5601

## Detailed Documentation

For comprehensive documentation including scripts, dashboards, troubleshooting, and best practices, see:
**[`../docs/TRACING_AND_OBSERVABILITY.md`](../docs/TRACING_AND_OBSERVABILITY.md)**

## Directory Structure

```
ops/
├── README.md                          # This file
├── scripts/                           # Provisioning and testing scripts
│   ├── provision-logs-pipeline.sh     # Set up Kafka → OpenSearch log pipeline
│   ├── provision-tracing.sh           # Set up Zipkin → OpenSearch tracing
│   ├── smoke-logs.sh                  # Verify log pipeline
│   ├── smoke-tracing.sh               # Verify tracing infrastructure
│   ├── generate-logs.sh               # Generate bulk sample logs
│   └── produce-log.sh                 # Produce single log message
├── opensearch/
│   ├── index-templates/               # OpenSearch index templates
│   │   └── service-logs-template.json # Template for structured logs
│   └── saved_objects/                 # Dashboards and visualizations
│       ├── zipkin-saved-objects.ndjson   # Data views + saved searches
│       ├── tracing-dashboard.ndjson      # Main tracing dashboard
│       └── tracing-advanced.ndjson       # Advanced metrics dashboard
├── connectors/
│   └── service-logs-opensearch.json   # Kafka Connect sink configuration
└── plugins/
    └── opensearch-connector-for-apache-kafka/  # Kafka Connect plugin
```

## Scripts Reference

### Provisioning
- `provision-logs-pipeline.sh` - Set up log pipeline (index templates, Kafka Connect)
- `provision-tracing.sh` - Verify Zipkin and OpenSearch integration

### Testing
- `smoke-logs.sh` - Quick test of log pipeline
- `smoke-tracing.sh` - Quick test of tracing infrastructure

### Data Generation
- `generate-logs.sh` - Generate 30-50 sample log messages
- `produce-log.sh` - Send single log message to Kafka
- `../scripts/test-distributed-tracing.sh` - E2E workflow generating traces

## OpenSearch Dashboards

Pre-built dashboards available after import:

1. **Tracing Overview** - Core performance metrics
   - Latency distribution histogram
   - Traces by service (donut chart)
   - Average latency by service
   - Traces over time

2. **Tracing Advanced Metrics** - Deep performance analysis
   - P95 latency by service
   - Trace kind breakdown (SERVER/CLIENT/PRODUCER/CONSUMER)
   - Error rate by service (4xx/5xx)

3. **Saved Searches**
   - Recent Traces (last 15 minutes)
   - Slow Traces (>1 second duration)

## One-Time Plugin Setup

Install the OpenSearch sink connector plugin (only once per machine). The Connect image loads plugins from `./ops/plugins`:

- Recommended: Aiven OpenSearch Sink Connector
  - Download binaries (zip/tar) from GitHub Releases: https://github.com/aiven/opensearch-connector-for-apache-kafka/releases/latest
  - Unpack so the plugin directory exists under: `ops/plugins/opensearch-connector-for-apache-kafka/`
  - The connector class is `io.aiven.kafka.connect.opensearch.OpensearchSinkConnector`
- Alternative: Confluent Elasticsearch Sink may not be compatible with OpenSearch; prefer the Aiven plugin.
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
