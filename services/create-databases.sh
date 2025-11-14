#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE productsdb;
    CREATE DATABASE inventorydb;
    CREATE DATABASE reviewsdb;
    CREATE DATABASE ordersdb;

    ALTER TABLE reviews 
    ADD COLUMN similarity_score DOUBLE PRECISION,
    ADD COLUMN most_similar_review_id BIGINT,
    ADD COLUMN evaluation_reason VARCHAR(500),
    ADD COLUMN evaluated_at TIMESTAMP;
EOSQL