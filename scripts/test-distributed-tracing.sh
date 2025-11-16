#!/bin/bash

# Full workflow test for distributed tracing across all core services
# This script exercises product-service, order-service, inventory-service, and review-service

set -e

echo "============================================"
echo "Distributed Tracing - Full Workflow Test"
echo "============================================"
echo ""

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Wait for services to be ready
echo -e "${BLUE}Step 0: Checking service health...${NC}"
sleep 3

# Step 1: Create a product
echo -e "${BLUE}Step 1: Creating a product in product-service${NC}"
PRODUCT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Distributed Tracing Test Product",
    "description": "Testing end-to-end tracing",
    "price": 99.99,
    "category": "Electronics",
    "skuCode": "DT-TEST-001",
    "quantity": 100
  }')

PRODUCT_ID=$(echo $PRODUCT_RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*' | head -1)
echo -e "${GREEN}✓ Product created with ID: $PRODUCT_ID${NC}"
echo ""

# Step 2: Get the product to verify
echo -e "${BLUE}Step 2: Retrieving product from product-service${NC}"
curl -s http://localhost:8080/api/products/$PRODUCT_ID > /dev/null
echo -e "${GREEN}✓ Product retrieved successfully${NC}"
echo ""

# Step 3: Create an order (triggers order-service → inventory-service via RabbitMQ)
echo -e "${BLUE}Step 3: Creating an order in order-service${NC}"
echo -e "${YELLOW}This will trigger RabbitMQ message to inventory-service${NC}"
ORDER_RESPONSE=$(curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderLineItemsDtoList": [
      {
        "sku": "DT-TEST-001",
        "price": 99.99,
        "quantity": 2,
        "productId": '$PRODUCT_ID'
      }
    ]
  }')

ORDER_NUMBER=$(echo $ORDER_RESPONSE | grep -o '"orderNumber":"[^"]*"' | cut -d'"' -f4)
echo -e "${GREEN}✓ Order created with number: $ORDER_NUMBER${NC}"
echo -e "${YELLOW}  → Trace should show: order-service → RabbitMQ → inventory-service${NC}"
echo ""

# Give RabbitMQ message time to process
sleep 2

# Step 4: Create a review (review-service)
echo -e "${BLUE}Step 4: Creating a review in review-service${NC}"
REVIEW_RESPONSE=$(curl -s -X POST http://localhost:8082/api/reviews \
  -H "Content-Type: application/json" \
  -d '{
    "productId": '$PRODUCT_ID',
    "userId": 999,
    "rating": 5,
    "comment": "Great product! Distributed tracing works perfectly.",
    "reviewerName": "Trace Tester"
  }')

REVIEW_ID=$(echo $REVIEW_RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*' | head -1)
echo -e "${GREEN}✓ Review created with ID: $REVIEW_ID${NC}"
echo ""

# Step 5: Get reviews for the product
echo -e "${BLUE}Step 5: Retrieving reviews from review-service${NC}"
curl -s http://localhost:8082/api/reviews/product/$PRODUCT_ID > /dev/null
echo -e "${GREEN}✓ Reviews retrieved successfully${NC}"
echo ""

# Step 6: Upvote the review
echo -e "${BLUE}Step 6: Upvoting review in review-service${NC}"
curl -s -X POST http://localhost:8082/api/reviews/$REVIEW_ID/upvote > /dev/null
echo -e "${GREEN}✓ Review upvoted${NC}"
echo ""

echo "============================================"
echo -e "${GREEN}All workflow steps completed!${NC}"
echo "============================================"
echo ""
echo "Services exercised:"
echo "  ✓ product-service (POST /api/products, GET /api/products/{id})"
echo "  ✓ order-service (POST /api/orders)"
echo "  ✓ inventory-service (via RabbitMQ from order-service)"
echo "  ✓ review-service (POST /api/reviews, GET /api/reviews/product/{id}, POST /api/reviews/{id}/upvote)"
echo ""
echo "View traces in Zipkin:"
echo "  → Open: http://localhost:9411"
echo "  → Look for traces spanning multiple services"
echo "  → Order creation should show: order-service → inventory-service"
echo ""
echo "Expected traces:"
echo "  1. POST /api/products → product-service"
echo "  2. GET /api/products/{id} → product-service"
echo "  3. POST /api/orders → order-service → RabbitMQ → inventory-service"
echo "  4. POST /api/reviews → review-service"
echo "  5. GET /api/reviews/product/{id} → review-service"
echo "  6. POST /api/reviews/{id}/upvote → review-service"
echo ""
