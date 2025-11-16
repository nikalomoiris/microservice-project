# Distributed Tracing and Observability Guide

This document provides comprehensive guidance on using the distributed tracing and observability tools in this microservices project.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Quick Start](#quick-start)
4. [Provisioning Scripts](#provisioning-scripts)
5. [Testing Scripts](#testing-scripts)
6. [Data Generation Scripts](#data-generation-scripts)
7. [OpenSearch Dashboards](#opensearch-dashboards)
8. [Troubleshooting](#troubleshooting)

---

## Overview

The project implements comprehensive distributed tracing and structured logging:

- **Distributed Tracing**: Zipkin with OpenSearch backend for persistent trace storage
- **Structured Logging**: Kafka-based log pipeline with OpenSearch storage
- **Correlation**: Traces and logs share `traceId` for complete request visibility
- **Visualization**: Pre-built OpenSearch Dashboards for analysis

**Key Components:**
- Zipkin (http://localhost:9411) - Trace collection and UI
- OpenSearch (http://localhost:9200) - Persistent storage
- OpenSearch Dashboards (http://localhost:5601) - Visualization and analysis
- Kafka - Log streaming pipeline
- logging-client library - Structured logging with trace context

---

## Architecture

### Trace Flow
```
Service Request → Spring Cloud Sleuth → Zipkin → OpenSearch (zipkin-span-*)
                                                              ↓
                                              OpenSearch Dashboards (visualization)
```

### Log Flow
```
Service → logging-client → Kafka (service-logs topic) → Kafka Connect → OpenSearch (service-logs-*)
                                                                                    ↓
                                                              OpenSearch Dashboards (correlation)
```

### Indices
- **zipkin-span-YYYY-MM-DD**: Daily trace span indices
  - Fields: `traceId`, `localEndpoint.serviceName`, `name`, `kind`, `duration`, `tags.*`
  - Time field: `timestamp_millis` (epoch milliseconds)
  
- **service-logs-YYYY-MM-DD**: Daily structured log indices
  - Fields: `@timestamp`, `level`, `message`, `service`, `traceId`, `spanId`, `metadata.*`
  - Time field: `@timestamp`

---

## Quick Start

### 1. Start Infrastructure
```bash
# From project root
docker-compose up -d
```

### 2. Provision Observability Stack
```bash
# Provision log pipeline (Kafka Connect, index templates)
./ops/scripts/provision-logs-pipeline.sh

# Provision tracing (index templates, verify Zipkin health)
./ops/scripts/provision-tracing.sh
```

### 3. Import Dashboards
```bash
# Import saved searches and basic dashboard
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

### 4. Generate Sample Data
```bash
# Generate trace data through complete workflow
./scripts/test-distributed-tracing.sh

# Generate multiple traces (30 iterations)
for i in {1..30}; do ./scripts/test-distributed-tracing.sh; sleep 0.3; done

# Generate structured logs
./ops/scripts/generate-logs.sh
```

### 5. Access Dashboards
- Zipkin UI: http://localhost:9411
- OpenSearch Dashboards: http://localhost:5601/app/dashboards

---

## Provisioning Scripts

Located in `ops/scripts/`:

### provision-logs-pipeline.sh
Provisions the Kafka-to-OpenSearch log pipeline.

**What it does:**
- Creates OpenSearch index template for `service-logs-*`
- Configures Kafka Connect with OpenSearch sink connector
- Sets up proper field mappings for structured logs

**Usage:**
```bash
./ops/scripts/provision-logs-pipeline.sh
```

**When to run:**
- First time setup
- After recreating OpenSearch container
- When modifying log index templates

---

### provision-tracing.sh
Provisions the distributed tracing infrastructure.

**What it does:**
- Verifies Zipkin health and OpenSearch connectivity
- Validates Zipkin's auto-created index templates
- Ensures proper daily index rotation

**Usage:**
```bash
./ops/scripts/provision-tracing.sh
```

**When to run:**
- First time setup
- After Zipkin or OpenSearch restart
- When troubleshooting trace storage issues

**Notes:**
- Zipkin manages its own index templates (do NOT create custom templates)
- Script validates but does not modify Zipkin's configuration

---

## Testing Scripts

Located in `scripts/`:

### test-distributed-tracing.sh
End-to-end workflow test that exercises the full service chain.

**What it does:**
1. Creates a product (product-service)
2. Retrieves the product (with trace context)
3. Creates an order for that product (order-service)
4. Retrieves the order
5. Checks inventory status (inventory-service)

**Usage:**
```bash
# Single workflow execution
./scripts/test-distributed-tracing.sh

# Generate heavy traffic (30 workflows)
for i in {1..30}; do ./scripts/test-distributed-tracing.sh; sleep 0.3; done

# Background generation
for i in {1..100}; do ./scripts/test-distributed-tracing.sh >/dev/null 2>&1; sleep 0.5; done &
```

**What to expect:**
- Creates one product per execution
- Creates one order per execution
- Generates 10-15 trace spans per workflow
- Includes HTTP requests, RabbitMQ events, database operations
- Average execution time: ~2-3 seconds

**Trace visualization:**
- View in Zipkin: http://localhost:9411
- Each workflow produces a complete trace tree showing:
  - REST API calls
  - Event publishing (RabbitMQ)
  - Event consumption
  - Database queries

---

### smoke-tracing.sh
Quick smoke test for tracing infrastructure.

**What it does:**
- Verifies Zipkin API is responsive
- Checks OpenSearch trace indices exist
- Validates span count > 0

**Usage:**
```bash
./ops/scripts/smoke-tracing.sh
```

**When to run:**
- After infrastructure startup
- To verify tracing is working
- During debugging

---

## Data Generation Scripts

Located in `ops/scripts/`:

### generate-logs.sh
Generates bulk structured log entries for testing.

**What it does:**
- Produces 30-50 structured log messages to Kafka
- Random distribution across services (order, inventory, product, review, logging)
- Random log levels (INFO, DEBUG, WARN, ERROR)
- Includes trace context (traceId, spanId)

**Usage:**
```bash
./ops/scripts/generate-logs.sh
```

**When to use:**
- Testing log pipeline
- Populating OpenSearch Dashboards
- Verifying Kafka Connect functionality

**Output:**
- Logs appear in `service-logs-*` indices
- View in OpenSearch Dashboards: http://localhost:5601/app/discover

---

### produce-log.sh
Produces a single structured log message to Kafka.

**What it does:**
- Sends one log entry to Kafka topic `service-logs`
- Used by `generate-logs.sh` for bulk generation

**Usage:**
```bash
./ops/scripts/produce-log.sh \
  "order-service" \
  "INFO" \
  "Order created successfully" \
  "trace-123" \
  "span-456" \
  '{"orderId":"12345","customerId":"67890"}'
```

**Parameters:**
1. Service name
2. Log level (INFO, DEBUG, WARN, ERROR)
3. Message text
4. Trace ID
5. Span ID
6. Metadata JSON object

---

### smoke-logs.sh
Quick smoke test for log pipeline.

**What it does:**
- Produces a test log message
- Verifies Kafka Connect is running
- Checks log indices exist in OpenSearch

**Usage:**
```bash
./ops/scripts/smoke-logs.sh
```

**When to run:**
- After log pipeline provisioning
- To verify Kafka → OpenSearch flow
- During pipeline troubleshooting

---

## OpenSearch Dashboards

Access at http://localhost:5601

### Pre-built Dashboards

#### 1. Tracing Overview
**Location:** Dashboards → "Tracing Overview"

**Visualizations:**
- **Latency Distribution**: Histogram of request durations (100ms buckets)
- **Traces by Service**: Donut chart showing trace volume per service
- **Average Latency by Service**: Horizontal bar chart of mean response times
- **Traces Over Time**: Time-series line chart of trace count

**Use cases:**
- Overall system performance monitoring
- Service load distribution
- Latency trends over time

---

#### 2. Tracing Advanced Metrics
**Location:** Dashboards → "Tracing Advanced Metrics"

**Visualizations:**
- **P95 Latency by Service**: 95th percentile response times (tail latency)
- **Trace Kind Breakdown**: Distribution of span types (SERVER, CLIENT, PRODUCER, CONSUMER)
- **Error Rate by Service**: HTTP 4xx and 5xx errors by service

**Use cases:**
- Performance SLA monitoring (p95, p99)
- Error rate tracking
- Understanding service communication patterns
- Identifying problematic services

---

### Saved Searches

#### Recent Traces (Zipkin)
**Location:** Discover → Open → "Recent Traces (Zipkin)"

**Columns:**
- Service Name
- Span Name
- Kind
- Duration (μs)
- Trace ID

**Filter:** Last 15 minutes

**Use case:** Quick view of recent activity

---

#### Slow Traces (Zipkin)
**Location:** Discover → Open → "Slow Traces (Zipkin)"

**Filter:** `duration > 1000000` (>1 second)

**Use case:** Identifying performance bottlenecks

---

### Data Views

#### zipkin-span-*
- **Time field:** `timestamp_millis`
- **Pattern:** Matches daily indices (zipkin-span-2025-11-16, etc.)
- **Key fields:**
  - `traceId`: Unique trace identifier
  - `localEndpoint.serviceName`: Service that created the span
  - `name`: Span operation name
  - `kind`: Span type (SERVER, CLIENT, PRODUCER, CONSUMER)
  - `duration`: Duration in microseconds
  - `tags.*`: Custom tags (http.status_code, error, etc.)

#### service-logs*
- **Time field:** `@timestamp`
- **Pattern:** Matches daily indices (service-logs-2025-11-16, etc.)
- **Key fields:**
  - `service`: Service name
  - `level`: Log level (INFO, WARN, ERROR, DEBUG)
  - `message`: Log message
  - `traceId`: Trace correlation ID
  - `spanId`: Span correlation ID
  - `metadata.*`: Structured metadata

---

### Creating Custom Visualizations

#### Example: Error Count by Service (Last Hour)
1. Go to **Visualize** → **Create visualization**
2. Select **Vertical Bar** chart
3. Select index pattern: **zipkin-span-***
4. Metrics:
   - Y-axis: Count
5. Buckets:
   - X-axis: Terms aggregation on `localEndpoint.serviceName`
6. Add filter: `tags.http.status_code: 5* OR tags.http.status_code: 4*`
7. Set time range: Last 1 hour
8. Save visualization

#### Example: Request Rate by Endpoint
1. **Visualize** → **Line** chart
2. Index: **zipkin-span-***
3. Metrics:
   - Y-axis: Count
4. Buckets:
   - X-axis: Date Histogram on `timestamp_millis` (interval: 1 minute)
   - Split series: Terms on `name` (top 5)
5. Filter: `kind: SERVER`
6. Save visualization

---

## Troubleshooting

### No Traces in Zipkin

**Check Zipkin health:**
```bash
curl http://localhost:9411/health
```

**Check OpenSearch connectivity:**
```bash
curl http://localhost:9200/_cat/indices/zipkin-*?v
```

**Verify spans exist:**
```bash
curl -s "http://localhost:9200/zipkin-span-*/_count" | jq '.count'
```

**Solution:**
- Ensure all services are running: `docker-compose ps`
- Check Zipkin logs: `docker-compose logs zipkin-service`
- Re-run provisioning: `./ops/scripts/provision-tracing.sh`

---

### No Logs in OpenSearch

**Check Kafka Connect:**
```bash
curl http://localhost:8085/connectors
curl http://localhost:8085/connectors/opensearch-logs-sink/status
```

**Check log indices:**
```bash
curl "http://localhost:9200/_cat/indices/service-logs-*?v"
```

**Verify log count:**
```bash
curl -s "http://localhost:9200/service-logs-*/_count" | jq '.count'
```

**Solution:**
- Re-provision pipeline: `./ops/scripts/provision-logs-pipeline.sh`
- Check Kafka Connect logs: `docker-compose logs kafka-connect`
- Verify connector status shows RUNNING

---

### Dashboards Empty

**Check time range:**
- OpenSearch Dashboards defaults to "Last 15 minutes"
- Expand to "Last 24 hours" or "Last 7 days"

**Verify data exists:**
```bash
# Check span count
curl -s "http://localhost:9200/zipkin-span-*/_count" | jq '.count'

# Check log count
curl -s "http://localhost:9200/service-logs-*/_count" | jq '.count'
```

**Generate fresh data:**
```bash
# Generate traces
./scripts/test-distributed-tracing.sh

# Generate logs
./ops/scripts/generate-logs.sh
```

**Refresh data view:**
1. Go to **Stack Management** → **Index Patterns**
2. Select **zipkin-span-*** or **service-logs***
3. Click **Refresh** icon
4. Return to dashboard and refresh browser

---

### Trace-Log Correlation Not Working

**Verify traceId in logs:**
```bash
curl -s "http://localhost:9200/service-logs-*/_search?size=1" | jq '.hits.hits[0]._source.traceId'
```

**Verify traceId in spans:**
```bash
curl -s "http://localhost:9200/zipkin-span-*/_search?size=1" | jq '.hits.hits[0]._source.traceId'
```

**Check logging-client configuration:**
- Services must use `logging-client` library
- MDC context must include trace/span IDs
- See `services/logging-client/STRUCTURED_LOGGING.md`

---

### High Latency in Traces

**Check slow queries:**
1. Open **Saved Search** → "Slow Traces (Zipkin)"
2. Identify services with duration > 1s
3. Drill into trace details in Zipkin UI

**Analyze by service:**
1. Open **Dashboard** → "Tracing Advanced Metrics"
2. Check **P95 Latency by Service**
3. Identify outliers

**Common causes:**
- Database query performance
- RabbitMQ message processing delays
- Network latency between services
- Unoptimized code paths

---

## Runbook: Trace Analysis

Follow these steps to investigate performance or reliability issues:

1) Identify slow traces
- Open Saved Search "Slow Traces (Zipkin)" or filter `duration > 1000000` (>1s)
- Sort by duration to find worst offenders

2) Drill into a trace
- In Zipkin, inspect the timeline to find long spans
- Check span tags (HTTP status, DB statement, custom tags like `order.number`)

3) Correlate logs
- Copy `traceId` from the span and search logs:
  ```bash
  curl -s "http://localhost:9200/service-logs-*/_search?q=traceId:<TRACE_ID>&size=50" | jq
  ```
- Look for ERROR/WARN entries around the time of the trace

4) Isolate problematic service
- Use the "P95 Latency by Service" chart to spot outliers
- Filter dashboard by `localEndpoint.serviceName` for deep-dive

5) Common remediations
- Add/adjust custom span tags for key business data
- Validate downstream dependencies (DB, RabbitMQ) for latency spikes
- Review sampling (increase temporarily for targeted endpoints)

---

## Performance Testing and Tuning

1) Baseline without tracing
```bash
wrk -t4 -c50 -d60s http://localhost:8081/api/orders   # example endpoint
```

2) With tracing enabled (default sampling)
```bash
export SPRING_PROFILES_ACTIVE=prod   # sampling.probability=0.1
wrk -t4 -c50 -d60s http://localhost:8081/api/orders
```

3) Compare metrics
- Request latency p50/p95/p99
- Throughput (RPS)
- CPU/memory on services

4) Tuning knobs
- `management.tracing.sampling.probability` (lower to reduce overhead)
- Prefer asynchronous work where possible to reduce critical path time
- Reduce heavy tags and large payload logging

5) Target acceptance
- Overhead < 5ms per request at p50
- Minimal GC/CPU impact at target RPS

---

## Best Practices

### Tracing
1. **Always include trace context** in HTTP headers and RabbitMQ messages
2. **Use meaningful span names** - include operation details
3. **Tag spans appropriately** - add custom tags for filtering
4. **Keep traces focused** - break large operations into logical spans
5. **Monitor tail latency** - use p95/p99 dashboards

### Logging
1. **Use structured logging** - leverage `logging-client` library
2. **Include trace context** - ensures correlation
3. **Add relevant metadata** - use metadata JSON for searchable fields
4. **Appropriate log levels** - ERROR for failures, WARN for issues, INFO for events
5. **Avoid sensitive data** - never log PII or credentials

### Dashboards
1. **Set appropriate time ranges** - match your analysis needs
2. **Use filters** - narrow down to specific services or operations
3. **Create custom visualizations** - tailor to your metrics
4. **Export/share dashboards** - save as NDJSON for team sharing
5. **Regular cleanup** - archive old indices to save storage

---

## References

- [Zipkin Documentation](https://zipkin.io/pages/architecture.html)
- [OpenSearch Documentation](https://opensearch.org/docs/latest/)
- [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth)
- [Kafka Connect](https://docs.confluent.io/platform/current/connect/index.html)

---

## Appendix: Script Locations

| Script | Location | Purpose |
|--------|----------|---------|
| provision-logs-pipeline.sh | ops/scripts/ | Provision log pipeline |
| provision-tracing.sh | ops/scripts/ | Provision tracing infrastructure |
| smoke-logs.sh | ops/scripts/ | Verify log pipeline |
| smoke-tracing.sh | ops/scripts/ | Verify tracing infrastructure |
| generate-logs.sh | ops/scripts/ | Generate bulk logs |
| produce-log.sh | ops/scripts/ | Produce single log |
| test-distributed-tracing.sh | scripts/ | E2E workflow test |

## Appendix: Saved Objects

| File | Location | Contents |
|------|----------|----------|
| zipkin-saved-objects.ndjson | ops/opensearch/saved_objects/ | Data view + 2 saved searches |
| tracing-dashboard.ndjson | ops/opensearch/saved_objects/ | Main dashboard + 4 visualizations |
| tracing-advanced.ndjson | ops/opensearch/saved_objects/ | Advanced dashboard + 3 visualizations |

---

**Last Updated:** November 16, 2025
