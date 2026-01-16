# Payment Service — Design Decisions & Learning Summary

This document captures key design decisions made during architecture & design phase for the payment-service. These decisions are informed by understanding async messaging patterns, idempotency, retention policies, and failure handling in distributed systems.

---

## 1. Async Messaging Architecture

### Decision: Use RabbitMQ for order-payment integration (not HTTP)

**Why:**
- **Non-blocking UX**: Order service doesn't wait for authorization to complete
- **Resilience**: Services can operate independently; payment-service downtime doesn't block orders
- **Decoupling**: Order-service doesn't need to know payment-service location/URL
- **Scalability**: Messages can be processed at payment-service's own pace

**Trade-offs Accepted:**
- Eventual consistency (delay between order creation and authorization)
- Must handle redelivery (duplicate events)
- Requires distributed tracing for debugging

---

## 2. Idempotency Strategy

### Decision: Check if payment already exists for orderId

**Pattern:**
```java
Optional<Payment> existing = paymentRepository.findByOrderId(event.getOrderId());
if (existing.isPresent()) {
    logger.info("Payment already exists, skipping");
    return; // Idempotent - don't process duplicate
}
```

**Why this works:**
- Once a payment exists for an order, it's been processed
- Duplicate RabbitMQ redeliveries will find it and skip
- Simple, efficient query on indexed field
- Doesn't lose data or require complex state tracking

**Key insight:** 
Idempotency is about **"Have I already done this work?"** not **"How many times did I try?"**

---

## 3. Payment State Machine

### Decision: Define explicit states with clear transitions

**States:**
- `CREATED` → Initial state after payment record created
- `AUTH_PENDING` → Authorization in progress or waiting for background retry
- `AUTHORIZED` → Authorization succeeded, ready for capture
- `FAILED` → Permanent failure (max retries exhausted)
- `CAPTURED` → Payment captured (money taken)
- `REFUNDED` → Payment refunded (wholly or partially)
- `VOIDED` → Authorization cancelled (e.g., order cancelled)

**Transitions:**
```
CREATED --[authorize]--> AUTH_PENDING --[success]--> AUTHORIZED
                                     --[max retries]--> FAILED
AUTHORIZED --[capture]--> CAPTURED
AUTHORIZED --[order cancelled]--> VOIDED
CAPTURED --[refund]--> REFUNDED
```

**Why this matters:**
- Prevents invalid transitions (can't capture before authorizing)
- Makes status queries meaningful (find all AUTHORIZED payments waiting for capture)
- Enables background jobs to find stuck payments (AUTH_PENDING)

---

## 4. Retry Strategy

### Decision: Use Spring RetryTemplate with exponential backoff

**Configuration:**
- Max attempts: 3 (configurable)
- Initial delay: 1 second
- Multiplier: 2.0 (1s → 2s → 4s)

**Which operations retry:**
- ✅ `authorize()` - transient failures (timeouts, 5xx errors)
- ✅ `capture()` - transient failures
- ✅ `refund()` - transient failures

**Which exceptions trigger retry:**
- ✅ Network timeouts
- ✅ Provider unavailable (5xx)
- ✅ Connection failures
- ❌ Card declined (won't succeed on retry)
- ❌ Invalid data (won't succeed on retry)

**Implementation:**
```java
@Retryable(
    value = {ProviderConnectionException.class, ProviderTimeoutException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public ProviderAuthResult authorize(Payment payment) {
    return paymentProvider.authorize(payment);
}

@Recover
public ProviderAuthResult recoverFromAuthFailure(Exception e, Payment payment) {
    logger.error("Authorization exhausted retries", e);
    return ProviderAuthResult.failed("Retries exhausted");
}
```

**Key insight:**
Retry logic should only handle **transient failures** (might succeed if retried). Non-transient failures (bad data, declined card) shouldn't be retried.

---

## 5. Stuck Payment Recovery

### Decision: Separate background job for long-running failures

**Pattern:**
1. **Immediate retries** happen in the event listener (via RetryTemplate)
2. **If retries exhaust**, mark payment as `AUTH_PENDING` with `retry_count`
3. **Background job** (scheduled every 5 minutes) picks up `AUTH_PENDING` payments and retries again

**Why separate:**
- Event listener shouldn't block waiting for recovery
- Can handle transient infrastructure issues (provider offline for 10 minutes)
- Decouples immediate retry from long-term resilience

**Background job:**
```java
@Scheduled(fixedDelay = 300000) // Every 5 minutes
public void retryPendingAuthorizations() {
    List<Payment> pending = paymentRepository.findByStatusAndRetryCountLessThan(
        PaymentStatus.AUTH_PENDING, 
        MAX_RETRIES
    );
    
    for (Payment payment : pending) {
        try {
            ProviderAuthResult result = paymentProvider.authorize(payment);
            if (result.isSuccess()) {
                payment.setStatus(PaymentStatus.AUTHORIZED);
                // ... publish success event
            }
        } catch (Exception e) {
            payment.setRetryCount(payment.getRetryCount() + 1);
            if (payment.getRetryCount() >= MAX_RETRIES) {
                payment.setStatus(PaymentStatus.FAILED);
                // ... publish failure event
            }
            paymentRepository.save(payment);
        }
    }
}
```

**Key insight:**
Three separate concerns must be handled independently:
1. **Idempotency** (duplicate events) - event deduplication
2. **Retry logic** (transient failures) - immediate retries in listener
3. **Recovery** (stuck payments) - background jobs

Confusing these leads to bugs!

---

## 6. Event Handling: order.cancelled

### Decision: Void authorization when order is cancelled

**Action:**
When `order.cancelled` event arrives:
- If payment status is `AUTHORIZED` (not yet captured)
  - Call provider.void() to release the held funds
  - Set payment status to `VOIDED`
  - Publish `payment.voided` event
- If payment status is `CAPTURED` or beyond
  - Consider refund (future phase)
- If payment status is `CREATED` or `AUTH_PENDING`
  - Just mark as `VOIDED` (no void call needed)

**Why:**
- Customer's funds are released immediately (UX benefit)
- Clean separation: cancelled order = no money held
- Prevents accidental capture of cancelled orders

---

## 7. Data Retention Policy

### Table: payments

| Status | Retention | Reason |
|--------|-----------|--------|
| `CAPTURED`, `REFUNDED`, `VOIDED` | 7 years | Compliance, chargebacks, tax audits, legal disputes |
| `FAILED` | 1 year | Fraud analysis, failure pattern detection |
| `AUTH_PENDING` | 7 days | Active data, then investigate if still pending |

**Implementation:**
```java
@Scheduled(cron = "0 0 2 * * *") // 2 AM daily
public void cleanupOldPayments() {
    LocalDateTime cutoff = LocalDateTime.now().minus(365, ChronoUnit.DAYS);
    paymentRepository.deleteByStatusAndCreatedAtBefore(
        PaymentStatus.FAILED, 
        cutoff
    );
}
```

### Table: payment_events

**Retention: 7 years (match CAPTURED payment retention)**

**Why:**
- Complete audit trail needed for compliance
- Chargebacks require proving payment history
- Tax/legal disputes may need original transaction details
- Forensics (fraud, unauthorized refunds)

**Implementation:**
Archive old payments and their events together to maintain referential integrity.

---

## 8. Idempotency vs. Retention

### Key Insight

These are **independent concerns**:

- **Idempotency window:** How long we might receive duplicates (minutes to hours)
- **Retention window:** How long we keep data for business/legal reasons (years)

**Both can coexist:**
```java
// Idempotency check (works for years even after archival)
Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
if (existing.isPresent()) return; // Don't process duplicate

// Retention policy (separate concern)
// Archive payments older than 7 years to cold storage
```

A payment archived 5 years ago can still prevent a duplicate from being processed!

---

## 9. Three Concerns: Inventory Service Learning

### Problem Identified

The existing inventory-service `OrderEventListener.handleOrderCreatedEvent()` is **NOT idempotent**:
- Receives `order.created` event
- Reserves inventory
- **If crashes before ACK:** RabbitMQ redelivers
- **No check** → reserves inventory AGAIN (double reservation!)

### Solution Applied

Created `reserved_inventory` table to track:

```sql
CREATE TABLE reserved_inventory (
    order_number VARCHAR(255) NOT NULL,
    inventory_id BIGINT NOT NULL,
    reserved_quantity INT NOT NULL,
    status VARCHAR(32), -- RESERVED, COMMITTED, RELEASED
    created_at TIMESTAMP,
    PRIMARY KEY (order_number, inventory_id),
    FOREIGN KEY (inventory_id) REFERENCES inventory(id)
);
```

**Idempotency check:**
```java
public void handleOrderCreatedEvent(OrderEvent event) {
    for (OrderLineItem item : event.getLineItems()) {
        // Already reserved this item for this order?
        if (alreadyReserved(event.getOrderNumber(), item.getProductId())) {
            continue; // Skip it
        }
        
        // Not reserved yet, reserve it
        inventoryService.reserveStock(item.getProductId(), item.getQuantity());
        reservedInventoryRepo.save(new ReservedInventory(...));
    }
}
```

**Key insight:** 
Each service needs its own strategy for idempotency based on its domain logic.

---

## 10. Why This Matters for Payment Service

The payment-service will follow the same principles:

1. **Check before processing** → prevents duplicate authorization
2. **State machine** → clear transitions prevent invalid states
3. **Retry logic separated from idempotency** → cleaner code
4. **Retention policy separate from idempotency** → flexibility
5. **Background jobs for recovery** → resilient system

All of these ideas are proven in the inventory-service and order-service already!

---

## 11. Open Design Questions (Resolved)

### Q: How long to keep payment_events?
**A:** 7 years (matches CAPTURED payment retention for compliance)

### Q: How to handle failed authorization retries?
**A:** Immediate retries in listener via RetryTemplate, then AUTH_PENDING state + background job

### Q: What if capture arrives before authorization completes?
**A:** Queue pending capture flag; capture succeeds once authorization completes

### Q: Should payment service void authorization on order.cancelled?
**A:** Yes - releases funds immediately, cleaner state

### Q: Use RetryTemplate or @Retryable annotation?
**A:** RetryTemplate (explicit, easier to understand while learning)

---

## Learning Outcomes

Through designing the payment-service, you've internalized:

✅ **Async messaging** - when and why to use event-driven architecture  
✅ **Idempotency** - how to make listeners resilient to redelivery  
✅ **Distributed state machines** - designing clear transitions and queries  
✅ **Retry strategies** - distinguishing transient from permanent failures  
✅ **Data retention** - aligning data lifecycle with business needs  
✅ **System resilience** - decoupling concerns (idempotency, retry, recovery)  
✅ **Real-world debugging** - finding bugs in existing code (inventory-service)  

These principles apply beyond payments — to any distributed, event-driven system!

