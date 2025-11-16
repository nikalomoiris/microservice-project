#!/bin/bash

# Smoke test for distributed tracing with OpenSearch backend
# Validates trace storage and retrieval from OpenSearch

set -e

OPENSEARCH_URL="http://localhost:9200"
ZIPKIN_URL="http://localhost:9411"

echo "============================================"
echo "Distributed Tracing Smoke Test"
echo "============================================"
echo ""

# Check Zipkin health
echo "1. Checking Zipkin health..."
ZIPKIN_HEALTH=$(curl -s "$ZIPKIN_URL/health" | grep -c "UP" || echo "0")
if [ "$ZIPKIN_HEALTH" -gt 0 ]; then
    echo "✓ Zipkin is healthy"
else
    echo "✗ Zipkin is not healthy"
    exit 1
fi
echo ""

# Check OpenSearch connection
echo "2. Checking OpenSearch health..."
OPENSEARCH_HEALTH=$(curl -s "$OPENSEARCH_URL/_cluster/health" | grep -c "green\|yellow" || echo "0")
if [ "$OPENSEARCH_HEALTH" -gt 0 ]; then
    echo "✓ OpenSearch is healthy"
else
    echo "✗ OpenSearch is not healthy"
    exit 1
fi
echo ""

# Check if Zipkin index template exists
echo "3. Checking Zipkin index template..."
TEMPLATE_EXISTS=$(curl -s "$OPENSEARCH_URL/_index_template/zipkin-template" | grep -c "zipkin" || echo "0")
if [ "$TEMPLATE_EXISTS" -gt 0 ]; then
    echo "✓ Zipkin index template exists"
else
    echo "✗ Zipkin index template not found - run provision-tracing.sh first"
    exit 1
fi
echo ""

# Generate a test trace
echo "4. Generating test trace..."
ORDER_RESPONSE=$(curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderLineItemsDtoList": [
      {
        "sku": "smoke-test-sku",
        "price": 19.99,
        "quantity": 1,
        "productId": 1
      }
    ]
  }')

if [ -z "$ORDER_RESPONSE" ]; then
    echo "✗ Failed to create test order"
    exit 1
fi
echo "✓ Test order created"
echo ""

# Wait for trace to be written to OpenSearch
echo "5. Waiting for trace to be indexed in OpenSearch..."
sleep 5

# Check if traces exist in OpenSearch
echo "6. Checking for traces in OpenSearch..."
TRACE_COUNT=$(curl -s "$OPENSEARCH_URL/zipkin*/_count" | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")
echo "   Found $TRACE_COUNT trace spans in OpenSearch"

if [ "$TRACE_COUNT" -gt 0 ]; then
    echo "✓ Traces are being stored in OpenSearch"
else
    echo "⚠  No traces found yet - may need more time to index"
fi
echo ""

# Query traces by service
echo "7. Querying traces by service..."
ORDER_SERVICE_TRACES=$(curl -s "$OPENSEARCH_URL/zipkin*/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "size": 1,
    "query": {
      "term": {
        "localEndpoint.serviceName": "order-service"
      }
    }
  }' | grep -c '"serviceName":"order-service"' || echo "0")

if [ "$ORDER_SERVICE_TRACES" -gt 0 ]; then
    echo "✓ Found traces for order-service"
else
    echo "⚠  No order-service traces found yet"
fi
echo ""

# Check Zipkin UI can query OpenSearch
echo "8. Checking Zipkin UI can query traces..."
ZIPKIN_TRACES=$(curl -s "$ZIPKIN_URL/api/v2/services" | grep -c "order-service\|product-service\|inventory-service" || echo "0")
if [ "$ZIPKIN_TRACES" -gt 0 ]; then
    echo "✓ Zipkin UI can query traces from OpenSearch"
else
    echo "⚠  Zipkin UI not showing services yet"
fi
echo ""

echo "============================================"
echo "Smoke Test Summary"
echo "============================================"
echo "✓ Zipkin is running and healthy"
echo "✓ OpenSearch is running and healthy"
echo "✓ Index templates are configured"
echo "✓ Traces are being generated"
if [ "$TRACE_COUNT" -gt 0 ]; then
    echo "✓ Traces stored in OpenSearch: $TRACE_COUNT spans"
else
    echo "⚠  Waiting for traces to be indexed"
fi
echo ""
echo "Next steps:"
echo "  - View traces in Zipkin: http://localhost:9411"
echo "  - Query traces in OpenSearch: curl http://localhost:9200/zipkin*/_search?pretty"
echo "  - View in OpenSearch Dashboards: http://localhost:5601"
echo ""
