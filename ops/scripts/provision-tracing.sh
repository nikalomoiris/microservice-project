#!/bin/bash

# Script to provision OpenSearch for distributed tracing
# Creates index templates and sample queries for trace-log correlation

set -e

OPENSEARCH_URL="http://localhost:9200"
TEMPLATE_DIR="$(dirname "$0")/../opensearch/index-templates"

echo "============================================"
echo "OpenSearch Tracing Setup"
echo "============================================"
echo ""

# Wait for OpenSearch to be ready
echo "Waiting for OpenSearch to be ready..."
until curl -s "$OPENSEARCH_URL/_cluster/health" > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo -e "\n✓ OpenSearch is ready"
echo ""

# IMPORTANT: Zipkin manages its own index templates. Do not install a custom one.
echo "Skipping custom Zipkin index template (Zipkin will manage templates)."
echo "Verifying Zipkin storage connectivity..."
HEALTH=$(curl -s "http://localhost:9411/health" | grep -c 'ElasticsearchStorage' || true)
if [ "$HEALTH" -gt 0 ]; then
    echo "✓ Zipkin reports Elasticsearch storage UP"
else
    echo "⚠  Could not verify Zipkin storage via /health. Ensure zipkin-service is running."
fi
echo ""

echo "============================================"
echo "Setup Complete!"
echo "============================================"
echo ""
echo "Zipkin index templates: managed by Zipkin at runtime"
echo ""
echo "Useful queries:"
echo ""
echo "1. Find all traces for a service:"
echo "   curl '$OPENSEARCH_URL/zipkin*/_search?pretty' -H 'Content-Type: application/json' -d '"
echo "   {"
echo "     \"query\": {"
echo "       \"term\": {"
echo "         \"localEndpoint.serviceName\": \"order-service\""
echo "       }"
echo "     }"
echo "   }'"
echo ""
echo "2. Find traces with high duration (> 1 second):"
echo "   curl '$OPENSEARCH_URL/zipkin*/_search?pretty' -H 'Content-Type: application/json' -d '"
echo "   {"
echo "     \"query\": {"
echo "       \"range\": {"
echo "         \"duration\": {"
echo "           \"gte\": 1000000"
echo "         }"
echo "       }"
echo "     }"
echo "   }'"
echo ""
echo "3. Correlate traces with logs (by traceId):"
echo "   curl '$OPENSEARCH_URL/service-logs*/_search?pretty' -H 'Content-Type: application/json' -d '"
echo "   {"
echo "     \"query\": {"
echo "       \"term\": {"
echo "         \"traceId\": \"YOUR_TRACE_ID\""
echo "       }"
echo "     }"
echo "   }'"
echo ""
echo "4. Count traces by service:"
echo "   curl '$OPENSEARCH_URL/zipkin*/_search?pretty' -H 'Content-Type: application/json' -d '"
echo "   {"
echo "     \"size\": 0,"
echo "     \"aggs\": {"
echo "       \"services\": {"
echo "         \"terms\": {"
echo "           \"field\": \"localEndpoint.serviceName\""
echo "         }"
echo "       }"
echo "     }"
echo "   }'"
echo ""
