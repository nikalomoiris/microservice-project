#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE productsdb;
    CREATE DATABASE inventorydb;
    CREATE DATABASE reviewsdb;
    CREATE DATABASE ordersdb;
EOSQL