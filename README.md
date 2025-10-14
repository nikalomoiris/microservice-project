# microservices-project

This project is a monorepo for a collection of microservices. It includes services for managing products, inventory, and reviews.

## Modules

This project contains the following microservices:

*   **product-service:** Manages products and categories.
*   **inventory-service:** Manages product inventory.
*   **review-service:** Manages product reviews.

## Prerequisites

To build and run this project, you will need:

*   Java 17
*   Maven
*   Docker

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
*   `product-service` on port `8080`
*   `inventory-service` on port `8082`
*   `review-service` on port `8083`

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
*   **`GET /api/products`**: Returns all products.
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
curl http://localhost:8082/api/inventory/LP123

# Reserve 5 units of product with ID 1
curl -X POST -H "Content-Type: application/json" -d '5' http://localhost:8082/api/inventory/1/reserve

# Release 2 units of product with ID 1
curl -X POST -H "Content-Type: application/json" -d '2' http://localhost:8082/api/inventory/1/release
```

### Review Service

*   **`POST /api/reviews`**: Creates a new review.
*   **`GET /api/reviews`**: Returns all reviews.
*   **`GET /api/reviews/{id}`**: Returns a review by its ID.
*   **`GET /api/reviews/product/{productId}`**: Returns all reviews for a given product.

**Example `curl` commands:**

```bash
# Create a new review for product with ID 1
curl -X POST -H "Content-Type: application/json" -d '{
    "productId": 1,
    "rating": 5,
    "comment": "This is a great laptop!"
}' http://localhost:8083/api/reviews

# Get all reviews for product with ID 1
curl http://localhost:8083/api/reviews/product/1
```