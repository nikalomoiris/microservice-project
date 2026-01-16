# The Three Concerns of Distributed Systems

A visual guide to the most important concept you've learned.

---

## Problem Statement

Your payment-service receives events from RabbitMQ:
- Sometimes the same event arrives **twice** (redelivery)
- Sometimes the event arrives but **fails partway** (network timeout)
- Sometimes the **provider is temporarily unavailable** (can retry later)

How do you handle all three?

---

## Solution: Three Independent Concerns

```
         ┌─────────────────────────────────────┐
         │  EVENT ARRIVES                      │
         └──────────────────┬──────────────────┘
                            │
                            ▼
         ┌─────────────────────────────────────┐
         │  CONCERN #1: IDEMPOTENCY           │
         │  "Have I already done this?"        │
         │  ✓ Fast database query              │
         │  ✓ Skip if payment exists          │
         └──────────────────┬──────────────────┘
                            │
                    NO      │      YES
                   ┌────────┴────────┐
                   │                 │
                   ▼                 ▼
         ┌────────────────┐  ┌──────────────┐
         │ PROCEED WITH   │  │ LOG AND      │
         │ PROCESSING     │  │ RETURN       │
         │                │  │ (IDEMPOTENT) │
         └────────┬───────┘  └──────────────┘
                  │
                  ▼
      ┌─────────────────────────────────────┐
      │  CONCERN #2: RETRY LOGIC           │
      │  "Will this work if I try again?"   │
      │  ✓ RetryTemplate (3 attempts)       │
      │  ✓ Exponential backoff              │
      │  ✓ Check if transient error         │
      └─────────────┬───────────────────────┘
                    │
        ┌───────────┼───────────┐
        │           │           │
      SUCCESS    TRANSIENT   NON-TRANSIENT
        │        FAILURE     FAILURE
        │           │           │
        ▼           ▼           ▼
    ┌───────┐  ┌──────────┐  ┌────────┐
    │UPDATE │  │MARK      │  │MARK    │
    │STATUS │  │AUTH_     │  │FAILED  │
    │PUBLISH│  │PENDING   │  │PUBLISH │
    │EVENT  │  │RETRY CNT │  │EVENT   │
    └───────┘  └─────┬────┘  └────────┘
                      │
                      ▼
          ┌─────────────────────────────────────┐
          │  CONCERN #3: RECOVERY JOB          │
          │  "What about stuck payments?"       │
          │  ✓ Background job (every 5 min)    │
          │  ✓ Query AUTH_PENDING with retries │
          │  ✓ Max 10 total attempts           │
          └─────────────────────────────────────┘
```

---

## Three Independent Solutions

### Concern #1: Idempotency
**Question:** "Have I already processed this event?"
**Answer:** Check if payment exists for orderId
**Code:**
```java
Optional<Payment> existing = repo.findByOrderId(event.getOrderId());
if (existing.isPresent()) {
    logger.info("Already processed");
    return; // IDEMPOTENT
}
// Process new event
```
**Why this works:** Once created, payment persists even if service crashes. Duplicate events find it and skip.

**Scope:** ALL event listeners (order.created, order.confirmed, order.cancelled)

**Timing:** Fast (milliseconds) — must be first check

---

### Concern #2: Retry Logic
**Question:** "Will this work if I try again?"
**Answer:** RetryTemplate with exponential backoff
**Code:**
```java
try {
    ProviderAuthResult result = retryTemplate.execute(ctx -> {
        return paymentProvider.authorize(payment);
    });
} catch (Exception e) {
    // Mark AUTH_PENDING for background job
    payment.setStatus(PaymentStatus.AUTH_PENDING);
}
```
**Why this works:** 
- Network timeout on attempt 1? Try again with delay.
- Network timeout on attempt 2? Try again with longer delay.
- Network timeout on attempt 3? Give up, mark AUTH_PENDING.

**Retry on:** Network errors, timeouts, 5xx
**Don't retry:** Card declined, invalid data (won't change)

**Scope:** Provider operations (authorize, capture, refund)

**Timing:** Seconds (1s → 2s → 4s)

---

### Concern #3: Recovery Job
**Question:** "What about payments stuck for hours/days?"
**Answer:** Scheduled background job
**Code:**
```java
@Scheduled(fixedDelay = 300000) // 5 minutes
public void retryPendingAuthorizations() {
    List<Payment> pending = repo.findByStatusAndRetryCountLessThan(
        PaymentStatus.AUTH_PENDING,
        10 // max retries
    );
    for (Payment p : pending) {
        try {
            ProviderAuthResult r = provider.authorize(p);
            if (r.isSuccess()) {
                p.setStatus(AUTHORIZED);
                publishEvent(...);
            }
        } catch (Exception e) {
            p.setRetryCount(p.getRetryCount() + 1);
            if (p.getRetryCount() >= 10) {
                p.setStatus(FAILED);
                publishEvent(...);
            }
        }
        repo.save(p);
    }
}
```
**Why this works:**
- Provider offline for 30 seconds? Listener retries fail, marks AUTH_PENDING.
- Background job runs every 5 minutes → picks it up → succeeds → continues.
- Provider down for 2 hours? Job keeps retrying every 5 min.
- After 10 attempts over ~50 minutes, mark FAILED.

**Scope:** Stuck payments only (AUTH_PENDING)

**Timing:** Minutes to hours

---

## Visual Timeline: A Complete Journey

```
Time T+0:   order.created event arrives
            ├─ Concern #1: Check if payment exists → NO
            ├─ Concern #2: RetryTemplate authorize attempt #1 → Timeout!
            └─ Mark status: AUTH_PENDING, retry_count=1

Time T+2s:  RetryTemplate automatic retry attempt #2 → Timeout!
            └─ Still AUTH_PENDING, retry_count=1 (internal, not saved)

Time T+4s:  RetryTemplate automatic retry attempt #3 → Timeout!
            └─ All retries exhausted → save AUTH_PENDING, retry_count=1

Time T+10min: Background job runs
              ├─ Query: SELECT * FROM payments WHERE status='AUTH_PENDING'
              ├─ Find the stuck payment
              ├─ Concern #3: Attempt authorize again → SUCCESS!
              └─ Mark status: AUTHORIZED, publish payment.authorized event

Time T+15min: order.confirmed event arrives
              ├─ Concern #1: Check if payment exists → YES, status=AUTHORIZED ✓
              └─ Concern #2: RetryTemplate capture attempt #1 → SUCCESS!
                 └─ Mark status: CAPTURED, publish payment.captured event
```

**Key insight:** If provider was offline 0-10 minutes, immediate retries help.  
If provider was offline 10+ minutes, background job saves the day.

---

## Common Mistakes (And How to Avoid Them)

### Mistake #1: Confusing Idempotency with Retry
❌ **WRONG:** "If I retry, I'll be idempotent"
✅ **RIGHT:** 
- Idempotency = "Is this a duplicate?" (check first)
- Retry = "Will this work if I try again?" (separate)
- They're independent!

### Mistake #2: Retrying Non-Transient Failures
❌ **WRONG:** Retry when card is declined
✅ **RIGHT:** Only retry network/timeout errors
```java
if (e instanceof ProviderTimeoutException) {
    // RETRY
} else if (e instanceof CardDeclinedException) {
    // DON'T RETRY - mark FAILED
}
```

### Mistake #3: Not Publishing Events After Commit
❌ **WRONG:** Publish event, then save to DB
```java
publishEvent(...);  // Event sent immediately
save();             // What if this crashes?
```
✅ **RIGHT:** Save, then publish
```java
save();  // DB commit first
TransactionSynchronization.afterCommit(() -> {
    publishEvent(...);  // Now safe to publish
});
```

### Mistake #4: Background Job Retrying Idempotently-Checked Events
❌ **WRONG:** Background job rechecks idempotency
✅ **RIGHT:** Background job only retries provider operations
```java
// Background job doesn't check idempotency again
// It only retries authorize() call, not event processing
ProviderAuthResult result = provider.authorize(payment);
// If this succeeds (was transient failure), great!
// If it fails again, increment retry count
```

### Mistake #5: Infinite Retries
❌ **WRONG:** Keep retrying forever
✅ **RIGHT:** Cap total attempts (3 immediate + background up to 10 total)
```java
if (payment.getRetryCount() >= 10) {
    payment.setStatus(FAILED);  // Terminal state
    publishEvent(FAILED);
}
```

---

## Quick Decision Tree

```
Payment event arrives
│
├─ QUESTION 1: "Have I seen this before?"
│  ├─ YES → Log and skip (IDEMPOTENT)
│  └─ NO → Continue
│
├─ QUESTION 2: "Does this operation fail?"
│  ├─ SUCCESS → Update status, publish event
│  └─ FAILURE → Continue to Question 3
│
├─ QUESTION 3: "Will this work if I retry?"
│  ├─ MAYBE (transient failure) → Continue to Question 4
│  └─ NO (non-transient) → Mark FAILED, publish failure event
│
└─ QUESTION 4: "How many times have we tried?"
   ├─ < 3 times → Try again immediately (RetryTemplate)
   └─ >= 3 times → Mark AUTH_PENDING for background job
```

---

## The Unified Pattern

These three concerns form a complete resilience system:

```
IDEMPOTENCY (prevent doubles)
    ↓
IMMEDIATE RETRY (handle transient)
    ↓
BACKGROUND RECOVERY (handle long-term)
    ↓
TERMINAL STATE (AUTHORIZED or FAILED)
```

**Every service benefits from this pattern:**
- Payment service (yours!)
- Inventory service (you found the bug!)
- Order service (already uses this)
- Notification service (future)
- Shipping service (future)

Master this pattern here, and you'll recognize it everywhere.

