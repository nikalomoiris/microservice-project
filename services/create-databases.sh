#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE productsdb;
    CREATE DATABASE inventorydb;
    CREATE DATABASE reviewsdb;
    CREATE DATABASE ordersdb;

    \c reviewsdb

    -- Add evaluation metadata fields (from Phase 2)
    ALTER TABLE reviews 
    ADD COLUMN IF NOT EXISTS similarity_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS most_similar_review_id BIGINT,
    ADD COLUMN IF NOT EXISTS evaluation_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS evaluated_at TIMESTAMP;

    -- Add moderation audit fields (Phase 3)
    ALTER TABLE reviews 
    ADD COLUMN IF NOT EXISTS moderated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP;
EOSQL