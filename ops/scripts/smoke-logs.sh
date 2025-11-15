#!/usr/bin/env bash
set -euo pipefail

# End-to-end smoke: produce a log, then verify it is indexed in OpenSearch

OS_URL="http://localhost:9200"
INDEX="service-logs"

# 1) Produce and capture smokeId
SMOKE_ID=$("$(dirname "$0")"/produce-log.sh)
echo "Smoke ID: ${SMOKE_ID}"

# 2) Poll OpenSearch for the document
MAX_TRIES=30
SLEEP_SEC=2
QUERY='{ "query": { "bool": { "should": [ { "term": { "traceId": "'"${SMOKE_ID}"'" } }, { "term": { "metadata.smokeId": "'"${SMOKE_ID}"'" } } ] } } }'

for i in $(seq 1 "$MAX_TRIES"); do
  RES=$(curl -sS -X GET "${OS_URL}/${INDEX}/_search" -H 'Content-Type: application/json' -d "$QUERY" || true)
  HITS=$(echo "$RES" | jq -r '.hits.total.value // 0' 2>/dev/null || echo 0)
  if [[ "$HITS" -gt 0 ]]; then
    echo "Success: found ${HITS} hit(s) for smokeId=${SMOKE_ID}"
    exit 0
  fi
  echo "Waiting for indexing... (${i}/${MAX_TRIES})"
  sleep "$SLEEP_SEC"

done

echo "ERROR: Did not find smokeId=${SMOKE_ID} in OpenSearch index ${INDEX}" >&2
exit 1
