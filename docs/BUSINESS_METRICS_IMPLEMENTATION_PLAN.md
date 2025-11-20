# Business Metrics Implementation Plan

**Last Updated:** 2025-11-16

## Purpose
This plan details the implementation of business and operational metrics for all microservices using Spring Boot Actuator and Micrometer. Metrics will be exposed via `/actuator/metrics` and can be integrated with Prometheus/Grafana for dashboards and alerting.

---

## Table of Contents
1. Overview
2. Metrics by Service
   - Inventory Service
   - Product Service
   - Order Service
   - Review Service
   - Logging Service
3. Implementation Steps
4. Example Code Snippets
5. Dashboard & Alerting Recommendations
6. Testing & Validation
7. Future Extensions

---

## 1. Overview

Business metrics provide real-time visibility into key operational and domain KPIs, enabling:
- Health monitoring
- SLA tracking
- Alerting on business events (e.g., low inventory, failed orders)
- Data-driven decision making

Metrics will be implemented as custom gauges, counters, and timers using Micrometer's `MeterRegistry` in each service.

---

## 2. Metrics by Service

### Inventory Service
- `inventory.total_quantity` — Total stock across all products
- `inventory.reserved_quantity` — Total reserved stock
- `inventory.low_stock.count` — Number of products below threshold (e.g., <10 units)
- `inventory.product.count` — Number of distinct SKUs
- `inventory.out_of_stock.count` — Number of SKUs with zero quantity
- `inventory.last_update.timestamp` — Timestamp of last inventory update

### Product Service
- `product.count` — Total number of products
- `category.count` — Total number of categories
- `product.active.count` — Number of active/available products
- `product.inactive.count` — Number of inactive/discontinued products
- `product.last_added.timestamp` — Timestamp of last product addition

### Order Service
- `order.count` — Total number of orders
- `order.status.created` — Orders in CREATED status
- `order.status.confirmed` — Orders in CONFIRMED status
- `order.status.shipped` — Orders in SHIPPED status
- `order.status.failed` — Orders in RESERVATION_FAILED or CANCELLED status
- `order.avg.items_per_order` — Average number of items per order
- `order.last_created.timestamp` — Timestamp of last order creation
- `order.rate_per_minute` — Orders created per minute (counter)

### Review Service
- `review.count` — Total number of reviews
- `review.status.moderated` — Moderated reviews
- `review.status.pending` — Pending moderation
- `review.upvotes.sum` — Total upvotes across all reviews
- `review.downvotes.sum` — Total downvotes
- `review.avg.rating` — Average rating across all reviews
- `review.last_added.timestamp` — Timestamp of last review

### Logging Service
- `log.ingest.count` — Total logs ingested
- `log.error.count` — Number of ERROR-level logs
- `log.warn.count` — Number of WARN-level logs
- `log.rate_per_minute` — Log ingestion rate

---

## 3. Implementation Steps

### Step 1: Identify Data Sources
- Use JPA repositories for counts and aggregates
- Use service methods for business logic (e.g., low stock)
- Use event listeners for timestamps

### Step 2: Register Metrics
- Inject `MeterRegistry` into each service
- Register gauges for real-time values (counts, averages)
- Register counters for event rates (order creation, log ingestion)
- Register timers for durations (optional)

### Step 3: Expose Metrics
- Metrics auto-exposed via `/actuator/metrics`
- Add Prometheus endpoint if needed (`spring-boot-starter-actuator`, `micrometer-registry-prometheus`)

### Step 4: Integrate with Dashboards
- Configure Prometheus to scrape metrics endpoints
- Build Grafana dashboards for business KPIs
- Set up alerting rules (e.g., low inventory, spike in failed orders)

---

## 4. Example Code Snippets

**InventoryService.java**
```java
@Autowired
private MeterRegistry meterRegistry;

@PostConstruct
public void registerMetrics() {
    meterRegistry.gauge("inventory.total_quantity", inventoryRepository, repo -> repo.sumTotalQuantity());
    meterRegistry.gauge("inventory.reserved_quantity", inventoryRepository, repo -> repo.sumReservedQuantity());
    meterRegistry.gauge("inventory.low_stock.count", inventoryRepository, repo -> repo.countLowStock(10));
    meterRegistry.gauge("inventory.product.count", inventoryRepository, repo -> repo.countDistinctSku());
    meterRegistry.gauge("inventory.out_of_stock.count", inventoryRepository, repo -> repo.countOutOfStock());
}
```

**ProductService.java**
```java
@Autowired
private MeterRegistry meterRegistry;

@PostConstruct
public void registerMetrics() {
    meterRegistry.gauge("product.count", productRepository, repo -> repo.count());
    meterRegistry.gauge("category.count", categoryRepository, repo -> repo.count());
    meterRegistry.gauge("product.active.count", productRepository, repo -> repo.countByActive(true));
    meterRegistry.gauge("product.inactive.count", productRepository, repo -> repo.countByActive(false));
}
```

**OrderService.java**
```java
@Autowired
private MeterRegistry meterRegistry;

@PostConstruct
public void registerMetrics() {
    meterRegistry.gauge("order.count", orderRepository, repo -> repo.count());
    meterRegistry.gauge("order.status.created", orderRepository, repo -> repo.countByStatus(OrderStatus.CREATED));
    meterRegistry.gauge("order.status.confirmed", orderRepository, repo -> repo.countByStatus(OrderStatus.CONFIRMED));
    meterRegistry.gauge("order.status.shipped", orderRepository, repo -> repo.countByStatus(OrderStatus.SHIPPED));
    meterRegistry.gauge("order.status.failed", orderRepository, repo -> repo.countByStatus(OrderStatus.RESERVATION_FAILED) + repo.countByStatus(OrderStatus.CANCELLED));
    meterRegistry.gauge("order.avg.items_per_order", orderRepository, repo -> repo.avgItemsPerOrder());
}

public void createOrder(OrderRequest req) {
    // ...existing code...
    meterRegistry.counter("order.rate_per_minute").increment();
    // ...existing code...
}
```

**ReviewService.java**
```java
@Autowired
private MeterRegistry meterRegistry;

@PostConstruct
public void registerMetrics() {
    meterRegistry.gauge("review.count", reviewRepository, repo -> repo.count());
    meterRegistry.gauge("review.status.moderated", reviewRepository, repo -> repo.countByStatus(ReviewStatus.MODERATED));
    meterRegistry.gauge("review.status.pending", reviewRepository, repo -> repo.countByStatus(ReviewStatus.PENDING));
    meterRegistry.gauge("review.upvotes.sum", reviewRepository, repo -> repo.sumUpvotes());
    meterRegistry.gauge("review.downvotes.sum", reviewRepository, repo -> repo.sumDownvotes());
    meterRegistry.gauge("review.avg.rating", reviewRepository, repo -> repo.avgRating());
}
```

---

## 5. Dashboard & Alerting Recommendations

- **Inventory Dashboard:**
  - Total quantity, reserved quantity, low/out-of-stock counts
  - Alert: `inventory.low_stock.count > 0`
- **Product Dashboard:**
  - Product/category counts, active/inactive split
  - Alert: `product.inactive.count` spike
- **Order Dashboard:**
  - Orders per status, avg items/order, rate per minute
  - Alert: `order.status.failed > threshold`
- **Review Dashboard:**
  - Review counts, upvotes/downvotes, avg rating
  - Alert: `review.status.pending > threshold`
- **Logging Dashboard:**
  - Log ingestion rate, error/warn counts
  - Alert: `log.error.count` spike

---

## 6. Testing & Validation

- Unit test each metric registration (mock repo methods)
- Validate `/actuator/metrics` returns expected values
- Integration test with Prometheus/Grafana
- Simulate business events to verify counters/gauges update

---

## 7. Future Extensions

- Add timers for business process durations (e.g., order fulfillment time)
- Add distribution summaries (histograms) for quantities, ratings
- Implement per-category/product metrics for fine-grained analysis
- Integrate with distributed tracing for correlation
- Add custom tags (e.g., region, customer segment)

---

## References
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Metrics](https://micrometer.io/docs/concepts)
- [Prometheus Integration](https://micrometer.io/docs/registry/prometheus)
- [Grafana Dashboards](https://grafana.com/docs/grafana/latest/dashboards/)
