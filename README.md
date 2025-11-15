# microservices-project

![CI (Unit)](https://github.com/nikalomoiris/microservice-project/actions/workflows/ci.yml/badge.svg?branch=main)
![E2E Tests](https://github.com/nikalomoiris/microservice-project/actions/workflows/e2e.yml/badge.svg?branch=main)

This project is a monorepo for a collection of microservices. It includes services for managing products, inventory, reviews, orders, and centralized logging.

## Modules

This project contains the following microservices:

*   **product-service:** Manages products and categories with support for filtering and sorting.
*   **inventory-service:** Manages product inventory with stock reservation and release capabilities.
*   **review-service:** Manages product reviews with upvote/downvote functionality.
*   **order-service:** Manages customer orders with integration to inventory service.
*   **logging-service:** Centralized logging service that consumes logs from Kafka.
*   **logging-client:** Library module for publishing logs to Kafka from other services.

## Prerequisites

To build and run this project, you will need:

*   Java 21
*   Maven
*   Docker
*   Docker Compose

## Building the Project

To build the entire project, run the following command from the root directory:

```bash
mvn clean install
```

## Running the Services

To run the services, you can use the provided `docker-compose.yml` file.

```bash
docker-compose up -d
```

This will start the following services:
*   `postgres-service` on port `5432` (PostgreSQL database)
*   `rabbitmq-service` on ports `5672` (AMQP) and `15672` (Management UI)
*   `zookeeper-service` on port `2181` (Kafka coordination)
*   `kafka-service` on port `9092` (Message broker)
*   `product-service` on port `8080`
*   `order-service` on port `8081`
*   `review-service` on port `8082`
*   `inventory-service` on port `8083`
*   `logging-service` on port `8090`

## API Endpoints and cURL Commands

Here are the available API endpoints and some `curl` commands you can use to test the basic functionality.

### Product Service

#### Categories

*   **`POST /api/categories`**: Creates a new category.
*   **`GET /api/categories`**: Returns all categories.
*   **`GET /api/categories/{id}`**: Returns a category by its ID.
*   **`PUT /api/categories/{id}`**: Updates a category.
*   **`DELETE /api/categories/{id}`**: Deletes a category.

**Example `curl` commands:**

```bash
# Create a new category
curl -X POST -H "Content-Type: application/json" -d '{
    "name": "Electronics",
    "description": "Electronic devices"
}' http://localhost:8080/api/categories

# Get all categories
curl http://localhost:8080/api/categories
```

#### Products

*   **`POST /api/products`**: Creates a new product.
*   **`GET /api/products`**: Returns all products. Supports optional query parameters:
    *   `categoryName`: Filter products by category name
    *   `sortBy`: Sort by field (e.g., `name`, `price`)
    *   `sortDir`: Sort direction (`asc` or `desc`, defaults to `asc`)
*   **`GET /api/products/{id}`**: Returns a product by its ID.
*   **`PUT /api/products/{id}`**: Updates a product.
*   **`DELETE /api/products/{id}`**: Deletes a product.
*   **`POST /api/products/{id}/images`**: Uploads an image for a product.

**Example `curl` commands:**

```bash
# Create a new product (assuming a category with ID 1 exists)
curl -X POST -H "Content-Type: application/json" -d '{
    "name": "Laptop",
    "description": "A powerful laptop",
    "price": 1200.00,
    "sku": "LP123",
    "categoryIds": [1]
}' http://localhost:8080/api/products

# Get all products
curl http://localhost:8080/api/products

# Get products filtered by category name
curl http://localhost:8080/api/products?categoryName=Electronics

# Get products sorted by price in descending order
curl "http://localhost:8080/api/products?sortBy=price&sortDir=desc"

# Get product with ID 1
curl http://localhost:8080/api/products/1

# Update product with ID 1
curl -X PUT -H "Content-Type: application/json" -d '{
    "name": "Laptop Pro",
    "description": "A more powerful laptop",
    "price": 1500.00,
    "sku": "LP124",
    "categoryIds": [1]
}' http://localhost:8080/api/products/1

# Delete product with ID 1
curl -X DELETE http://localhost:8080/api/products/1
```

### Inventory Service

*   **`GET /api/inventory/{sku}`**: Returns inventory information for a given SKU.
*   **`POST /api/inventory/{productId}/reserve`**: Reserves a given quantity of a product.
*   **`POST /api/inventory/{productId}/release`**: Releases a given quantity of a product.

**Example `curl` commands:**

```bash
# Get inventory for SKU "LP123"
curl http://localhost:8083/api/inventory/LP123

# Reserve 5 units of product with ID 1
curl -X POST -H "Content-Type: application/json" -d '5' http://localhost:8083/api/inventory/1/reserve

# Release 2 units of product with ID 1
curl -X POST -H "Content-Type: application/json" -d '2' http://localhost:8083/api/inventory/1/release
```

### Review Service

*   **`POST /api/reviews`**: Creates a new review.
*   **`GET /api/reviews`**: Returns all reviews.
*   **`GET /api/reviews/{id}`**: Returns a review by its ID.
*   **`GET /api/reviews/product/{productId}`**: Returns all reviews for a given product.
*   **`POST /api/reviews/{id}/upvote`**: Adds an upvote to a review.
*   **`POST /api/reviews/{id}/downvote`**: Adds a downvote to a review.

**Example `curl` commands:**

```bash
# Create a new review for product with ID 1
curl -X POST -H "Content-Type: application/json" -d '{
    "productId": 1,
    "rating": 5,
    "comment": "This is a great laptop!"
}' http://localhost:8082/api/reviews

# Get all reviews for product with ID 1
curl http://localhost:8082/api/reviews/product/1

# Upvote review with ID 1
curl -X POST http://localhost:8082/api/reviews/1/upvote

# Downvote review with ID 1
curl -X POST http://localhost:8082/api/reviews/1/downvote
```

### Order Service

*   **`POST /api/orders`**: Creates a new order.

**Example `curl` commands:**

```bash
# Create a new order with multiple line items
curl -X POST -H "Content-Type: application/json" -d '{
    "orderLineItemsDtoList": [
        {
            "sku": "LP123",
            "price": 1200.00,
            "quantity": 1
        },
        {
            "sku": "MO456",
            "price": 25.00,
            "quantity": 2
        }
    ]
}' http://localhost:8081/api/orders
```

### Logging Service

The logging service consumes log messages from a Kafka topic (`service-logs`) and outputs them to the console. It runs on port `8090` but does not expose HTTP endpoints. Services can publish logs using the `logging-client` library.

## Infrastructure Components

The project uses the following infrastructure services:

*   **PostgreSQL**: Relational database for persistent storage (port `5432`)
*   **RabbitMQ**: Message broker for asynchronous communication between services
    *   AMQP port: `5672`
    *   Management UI: `http://localhost:15672` (username: `guest`, password: `guest`)
*   **Kafka**: Distributed event streaming platform for logging (port `9092`)
*   **Zookeeper**: Coordination service for Kafka (port `2181`)