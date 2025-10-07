# microservices-project

This project is a monorepo for a collection of microservices. It includes services for managing products, inventory, and reviews.

## Modules

This project contains the following microservices:

*   **product-service:** Manages products.
    *   `POST /api/products`: Creates a new product.
    *   `GET /api/products`: Returns all products.
    *   `GET /api/products/{id}`: Returns a product by its ID.
    *   `PUT /api/products/{id}`: Updates a product.
    *   `DELETE /api/products/{id}`: Deletes a product.
*   **inventory-service:** Manages product inventory. This service does not have any API endpoints and is intended to be used internally by other services.
*   **review-service:** Manages product reviews.
    *   `POST /api/reviews`: Creates a new review.
    *   `GET /api/reviews`: Returns all reviews.
    *   `GET /api/reviews/{id}`: Returns a review by its ID.
    *   `GET /api/reviews/product/{productId}`: Returns all reviews for a given product.

## Prerequisites

To build and run this project, you will need:

*   Java 17
*   Maven

## Building the Project

To build the entire project, run the following command from the root directory:

```bash
mvn clean install
```

## Running the Services

To run each microservice, navigate to its directory and run the following command:

### Product Service

```bash
cd services/product-service
mvn spring-boot:run
```

### Inventory Service

```bash
cd services/inventory-service
mvn spring-boot:run
```

### Review Service

```bash
cd services/review-service
mvn spring-boot:run
```
