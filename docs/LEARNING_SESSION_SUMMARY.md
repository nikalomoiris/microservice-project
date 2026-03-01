# Learning Session Summary — Payment Service Design

**Date:** January 15, 2026  
**Focus:** Async messaging, idempotency, distributed state machines, failure handling  
**Outcome:** Refined implementation plan with design decisions documented

---

## Key Learning Points

### 1. **Async Messaging vs. REST Trade-offs**
- **Async (chosen):** Non-blocking, resilient, decoupled, eventual consistency
- **REST (rejected):** Immediate response but tight coupling, cascading failures
- **Real-world lesson:** Different patterns for different use cases; async adds complexity but solves real problems

### 2. **Idempotency is Fundamental**
- RabbitMQ redelivery is a **feature**, not a bug (at-least-once delivery)
- Idempotency = "Have I already done this work?"
- **Pattern:** Query for existing resource, skip if found
- **Applies to:** Any distributed system with asynchronous messaging

### 3. **Three Independent Concerns (Don't Confuse!)**
- **Idempotency:** "Is this a duplicate event?"
- **Retry Logic:** "Will this work if I try again?" (transient failures only)
- **Recovery:** "What about stuck payments?" (background jobs)

Each has its own solution; mixing them creates bugs.

### 4. **State Machines Enable Distributed Systems**
- Explicit states (`CREATED`, `AUTH_PENDING`, `AUTHORIZED`, etc.)
- Valid transitions only
- Clear queries (e.g., "find all AUTH_PENDING")
- Prevents impossible states

### 5. **Data Lifecycle ≠ Idempotency Window**
- **Idempotency:** Minutes to hours (when duplicates might arrive)
- **Retention:** Years (compliance, disputes, audits)
- Both can coexist; they're independent

### 6. **Retry Strategy Distinction**
- **Transient failures:** Network timeouts, 5xx errors, connection issues → RETRY
- **Non-transient failures:** Card declined, invalid data, bad request → DON'T RETRY
- Retrying non-transient failures wastes resources

### 7. **Real Code Has Bugs**
- Found idempotency bug in `inventory-service/OrderEventListener`
- Applied learning: new `reserved_inventory` table with status tracking
- Same patterns/principles apply everywhere

---

## Design Decisions Made

| Decision | Rationale |
|----------|-----------|
| Use async RabbitMQ | Non-blocking, resilient to service outages |
| Check if payment exists for idempotency | Simple, efficient, works at scale |
| Separate retry + recovery jobs | Async listener shouldn't block on retries |
| Use RetryTemplate (not @Retryable) | Explicit code easier to learn and debug |
| Void authorization on order.cancelled | Releases funds immediately, clean state |
| Keep payment_events for 7 years | Compliance, chargebacks, legal disputes |
| Add `retry_count` to track attempts | Enables background job recovery logic |
| VOIDED state for cancelled orders | Clear state, prevents accidental captures |

---

## Architecture: Payment State Machine

```
┌─────────┐
│ CREATED │
└────┬────┘
     │ authorize() with RetryTemplate (3x)
     ├─ success → AUTHORIZED
     ├─ transient failure → AUTH_PENDING (background job retries)
     └─ non-transient failure → FAILED

┌──────────────┐
│ AUTH_PENDING │ ← Background job retries every 5 min
└────┬─────────┘
     ├─ retry succeeds → AUTHORIZED
     └─ max retries (10 total) → FAILED

┌────────────┐
│ AUTHORIZED │
└────┬───────┘
     ├─ capture() → CAPTURED
     ├─ order.cancelled → VOIDED
     └─ refund → REFUNDED

┌─────────┐
│ VOIDED  │ (authorization released)
└─────────┘

┌─────────────┐
│ CAPTURED    │ → 7-year retention
└─────────────┘

┌──────────┐
│ REFUNDED │ → 7-year retention
└──────────┘

┌────────┐
│ FAILED │ → 1-year retention
└────────┘
```

---

## Idempotency Pattern

```java
@RabbitListener(queues = "order.created.queue")
public void handleOrderCreated(OrderEvent event) {
    // IDEMPOTENCY CHECK (must come first!)
    Optional<Payment> existing = paymentRepository.findByOrderId(event.getOrderId());
    if (existing.isPresent()) {
        logger.info("Payment already exists for order {}, skipping", event.getOrderId());
        return; // Duplicate event - don't process
    }
    
    // NOT A DUPLICATE - process normally
    Payment payment = new Payment(...);
    paymentRepository.save(payment);
    
    // RETRY LOGIC (separate concern)
    try {
        ProviderAuthResult result = retryTemplate.execute(ctx -> {
            return paymentProvider.authorize(payment);
        });
        // handle result
    } catch (Exception e) {
        // Mark AUTH_PENDING for background job
        payment.setStatus(PaymentStatus.AUTH_PENDING);
        paymentRepository.save(payment);
    }
}
```

**Key points:**
- Check for existing payment FIRST (before any work)
- If found, skip everything
- If not found, proceed with normal flow
- Retries happen separately (don't confuse with idempotency)

---

## Files Created/Updated

### New Files
- [`docs/PAYMENT_SERVICE_DESIGN_DECISIONS.md`](docs/PAYMENT_SERVICE_DESIGN_DECISIONS.md) — comprehensive design decisions with rationale

### Updated Files
- [`docs/NEW_SERVICE_IMPLEMENTATION_PLAN.md`](docs/NEW_SERVICE_IMPLEMENTATION_PLAN.md) — refined with:
  - Detailed idempotency strategy
  - Three-concern failure handling
  - 9-phase implementation roadmap
  - Resolved open questions
  - Future enhancement backlog

---

## Next Steps: Ready to Code

You now have:
✅ Clear design decisions documented  
✅ State machine architecture understood  
✅ Idempotency pattern to follow  
✅ Retry strategy defined  
✅ Data model with indexes  
✅ 9-phase implementation roadmap  

**When you're ready to start coding:**
1. Begin Phase 1 (Foundation)
2. Create the payment-service module
3. Define domain model and database schema
4. Implement provider abstraction
5. Write tests as you go (not after)

**Throughout implementation, refer back to:**
- This summary (why decisions were made)
- `PAYMENT_SERVICE_DESIGN_DECISIONS.md` (detailed rationale)
- `NEW_SERVICE_IMPLEMENTATION_PLAN.md` (technical roadmap)

---

## Key Takeaway

The payment-service isn't just about payments—it's a **teaching project** for building resilient, event-driven distributed systems. Every pattern you learn here applies to:
- Inventory reservations (already fixed!)
- Order fulfillment
- Notification systems
- Any async, event-driven architecture

Master these patterns here, and you can build scalable systems anywhere.

