# Review Evaluation Utility — Implementation Plan

**Document Version:** 1.0  
**Created:** 2025-11-09  
**Target Service:** review-service  
**Objective:** Implement similarity-based review evaluation to automatically classify new reviews as APPROVED, FOR_MODERATION, or REJECTED before saving to the database.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Business Requirements](#business-requirements)
3. [Technical Architecture](#technical-architecture)
4. [Data Model Changes](#data-model-changes)
5. [API Design](#api-design)
6. [Similarity Detection Algorithm](#similarity-detection-algorithm)
7. [Implementation Tasks](#implementation-tasks)
8. [Testing Strategy](#testing-strategy)
9. [Configuration & Tuning](#configuration--tuning)
10. [Deployment Plan](#deployment-plan)
11. [Monitoring & Observability](#monitoring--observability)
12. [Risks & Mitigation](#risks--mitigation)
13. [Future Enhancements](#future-enhancements)

---

## Executive Summary

### Purpose
Implement an automated review evaluation system that analyzes new review submissions for similarity to existing reviews. This prevents spam, duplicate content, and bot-generated reviews while reducing manual moderation workload.

### Approach
- **Pre-save evaluation**: Reviews are evaluated before persistence
- **Similarity-based classification**: Uses text similarity algorithms (TF-IDF + Cosine Similarity, Levenshtein Distance)
- **Three-tier classification**: APPROVED (auto-accept), FOR_MODERATION (human review), REJECTED (auto-block)
- **Product-scoped analysis**: Compare against existing reviews for the same product
- **Configurable thresholds**: Tunable similarity thresholds for different classification tiers

### Success Criteria
- ✅ No duplicate/near-duplicate reviews saved with APPROVED status
- ✅ Spam/bot reviews automatically flagged or rejected
- ✅ Legitimate reviews approved within 200ms
- ✅ Clear audit trail for all evaluation decisions
- ✅ Zero downtime deployment

---

## Business Requirements

### Functional Requirements

**FR-1: Review Evaluation Before Persistence**
- All new review submissions must be evaluated before saving to database
- Evaluation compares against existing reviews for the same productId
- Evaluation result determines initial review status

**FR-2: Three-Tier Classification System**
- **APPROVED**: Review is unique enough, auto-approved and visible
- **FOR_MODERATION**: Review is somewhat similar, requires human review
- **REJECTED**: Review is too similar/duplicate, auto-rejected and hidden

**FR-3: Similarity Metrics**
- Text similarity score (0.0 - 1.0) calculated against each existing review
- Highest similarity score determines classification
- Configurable thresholds for APPROVED/FOR_MODERATION/REJECTED boundaries

**FR-4: Audit Trail**
- Store similarity score and evaluation reason with each review
- Track which existing review(s) triggered similarity match
- Include timestamp and evaluator metadata

### Non-Functional Requirements

**NFR-1: Performance**
- Review submission response time < 500ms (p95)
- Similarity calculation < 200ms for products with up to 1000 reviews
- No impact on existing GET endpoints

**NFR-2: Scalability**
- Support products with up to 5000 reviews
- Batch evaluation optimization for large review sets
- Caching strategy for frequently accessed products

**NFR-3: Maintainability**
- Externalized configuration for thresholds
- Clear separation of concerns (evaluation logic in separate service class)
- Comprehensive logging for debugging

**NFR-4: Data Integrity**
- Backward compatible schema changes (existing reviews remain unaffected)
- Default status for existing reviews: APPROVED
- No data loss during migration

---

## Technical Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        ReviewController                          │
│                     POST /api/reviews                            │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ReviewService                             │
│  - createReview(Review)                                          │
│  - Pre-evaluation orchestration                                 │
└────────────┬───────────────────────────┬────────────────────────┘
             │                           │
             ▼                           ▼
┌────────────────────────┐   ┌──────────────────────────────────┐
│ ReviewEvaluationService │   │    ReviewRepository              │
│ - evaluate(Review)      │   │    - findByProductId()           │
│ - calculateSimilarity() │   │    - save()                      │
│ - determineStatus()     │   └──────────────────────────────────┘
└────────────┬───────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────────┐
│              SimilarityCalculator (Interface)                   │
│                                                                  │
│  Implementations:                                                │
│  - TfIdfCosineSimilarityCalculator                              │
│  - LevenshteinSimilarityCalculator                              │
│  - CompositeSimilarityCalculator (weighted average)             │
└──────────────────────────────────────────────────────────────────┘
```

### Key Components

**1. ReviewEvaluationService**
- **Responsibility**: Orchestrate review evaluation workflow
- **Methods**:
  - `EvaluationResult evaluate(Review newReview, List<Review> existingReviews)`
  - `ReviewStatus determineStatus(double similarityScore)`
- **Dependencies**: SimilarityCalculator, ReviewEvaluationConfig

**2. SimilarityCalculator Interface**
```java
public interface SimilarityCalculator {
    /**
     * Calculate similarity between two review comments
     * @return similarity score between 0.0 (completely different) and 1.0 (identical)
     */
    double calculateSimilarity(String comment1, String comment2);
}
```

**3. EvaluationResult (Domain Model)**
```java
public class EvaluationResult {
    private ReviewStatus status;
    private double maxSimilarityScore;
    private Long mostSimilarReviewId;
    private String evaluationReason;
    private Instant evaluatedAt;
}
```

**4. ReviewEvaluationConfig**
- Configuration bean for thresholds and feature flags
- Externalized to `application.properties`

---

## Data Model Changes

### Review Entity Schema Updates

**New Fields Added to `Review` Entity:**

```java
@Entity
@Table(name = "reviews")
public class Review {
    // ... existing fields ...
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewStatus status = ReviewStatus.APPROVED; // default for backward compatibility
    
    @Column(name = "similarity_score")
    private Double similarityScore;
    
    @Column(name = "most_similar_review_id")
    private Long mostSimilarReviewId;
    
    @Column(name = "evaluation_reason", length = 500)
    private String evaluationReason;
    
    @Column(name = "evaluated_at")
    private Instant evaluatedAt;
}
```

**New Enum: ReviewStatus**

```java
public enum ReviewStatus {
    APPROVED,           // Auto-approved, visible to users
    FOR_MODERATION,     // Flagged for manual review
    REJECTED            // Auto-rejected, hidden from users
}
```

### Database Migration Script

**Location:** `services/review-service/src/main/resources/db/migration/V2__add_review_evaluation_fields.sql`

```sql
-- Add status column (default APPROVED for backward compatibility)
ALTER TABLE reviews 
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

-- Add evaluation metadata columns
ALTER TABLE reviews 
ADD COLUMN similarity_score DECIMAL(5,4),
ADD COLUMN most_similar_review_id BIGINT,
ADD COLUMN evaluation_reason VARCHAR(500),
ADD COLUMN evaluated_at TIMESTAMP;

-- Add index for filtering by status
CREATE INDEX idx_reviews_status ON reviews(status);

-- Add index for product reviews with status filter (common query)
CREATE INDEX idx_reviews_product_status ON reviews(product_id, status);

-- Add foreign key constraint for most_similar_review_id (optional, for referential integrity)
ALTER TABLE reviews 
ADD CONSTRAINT fk_most_similar_review 
FOREIGN KEY (most_similar_review_id) 
REFERENCES reviews(id) 
ON DELETE SET NULL;
```

**Notes:**
- Use Flyway or Liquibase if project adopts versioned migrations
- Current setup uses `spring.jpa.hibernate.ddl-auto=update` — migration can be manual SQL or JPA-generated
- Consider adding this migration script and switching to Flyway for better control

---

## API Design

### Updated API Endpoints

**1. POST /api/reviews (Modified Behavior)**

**Request Body:**
```json
{
  "productId": 123,
  "rating": 5,
  "comment": "Great product! Fast shipping and excellent quality."
}
```

**Response Body (APPROVED):**
```json
{
  "id": 456,
  "productId": 123,
  "rating": 5,
  "comment": "Great product! Fast shipping and excellent quality.",
  "upvotes": 0,
  "downvotes": 0,
  "status": "APPROVED",
  "similarityScore": 0.32,
  "evaluatedAt": "2025-11-09T18:30:00Z"
}
```

**Response Body (FOR_MODERATION):**
```json
{
  "id": 457,
  "productId": 123,
  "rating": 5,
  "comment": "Great product! Fast delivery and excellent quality.",
  "upvotes": 0,
  "downvotes": 0,
  "status": "FOR_MODERATION",
  "similarityScore": 0.78,
  "mostSimilarReviewId": 456,
  "evaluationReason": "High similarity (78%) to existing review #456",
  "evaluatedAt": "2025-11-09T18:31:00Z"
}
```

**Response Body (REJECTED):**
```json
{
  "id": 458,
  "productId": 123,
  "rating": 5,
  "comment": "Great product! Fast shipping and excellent quality.",
  "upvotes": 0,
  "downvotes": 0,
  "status": "REJECTED",
  "similarityScore": 0.98,
  "mostSimilarReviewId": 456,
  "evaluationReason": "Near-duplicate (98%) of existing review #456",
  "evaluatedAt": "2025-11-09T18:32:00Z"
}
```

**HTTP Status Codes:**
- `201 Created` — All cases (review is created even if rejected, for audit purposes)
- `400 Bad Request` — Invalid input (missing required fields)
- `500 Internal Server Error` — Evaluation service failure

---

**2. GET /api/reviews/product/{productId} (Modified Behavior)**

**Query Parameters (New):**
- `status` (optional): Filter by review status (APPROVED, FOR_MODERATION, REJECTED)
- Default: Only return APPROVED reviews (backward compatible)

**Example:**
```
GET /api/reviews/product/123?status=APPROVED,FOR_MODERATION
```

**Response:**
```json
[
  {
    "id": 456,
    "productId": 123,
    "rating": 5,
    "comment": "Great product!",
    "status": "APPROVED",
    "upvotes": 10,
    "downvotes": 1
  },
  {
    "id": 457,
    "productId": 123,
    "rating": 4,
    "comment": "Good product.",
    "status": "FOR_MODERATION",
    "upvotes": 0,
    "downvotes": 0
  }
]
```

---

**3. GET /api/reviews (Modified Behavior)**

**Query Parameters (New):**
- `status` (optional): Filter by review status
- Default: Only return APPROVED reviews

---

**4. PATCH /api/reviews/{id}/status (New Endpoint — Admin Only)**

**Purpose:** Manual moderation — update review status

**Request Body:**
```json
{
  "status": "APPROVED",
  "moderationNote": "Reviewed by admin - legitimate review"
}
```

**Response:**
```json
{
  "id": 457,
  "status": "APPROVED",
  "moderatedBy": "admin-user-123",
  "moderatedAt": "2025-11-09T19:00:00Z"
}
```

**Note:** Requires authentication/authorization (future enhancement, placeholder for now)

---

## Similarity Detection Algorithm

### Algorithm Selection

**Primary Algorithm: TF-IDF + Cosine Similarity**

**Why:**
- Industry-standard for text similarity
- Handles variable-length text well
- Accounts for word importance (frequent words like "the" are weighted lower)
- Efficient for moderate-sized datasets (< 5000 reviews per product)

**Secondary Algorithm: Levenshtein Distance (Edit Distance)**

**Why:**
- Detects near-exact matches with minor typos
- Catches simple character substitutions (spam bots)
- Complements TF-IDF for short reviews

**Composite Strategy:**
- Use weighted average: `finalScore = 0.7 * cosineSimilarity + 0.3 * levenshteinSimilarity`
- Configurable weights via `application.properties`

---

### Implementation Pseudocode

**TF-IDF Cosine Similarity:**

```
1. Preprocess text (lowercase, remove punctuation, tokenize)
2. Build vocabulary from all reviews (new + existing)
3. Calculate TF-IDF vectors for each review
4. Calculate cosine similarity between new review and each existing review
5. Return max similarity score
```

**Levenshtein Similarity:**

```
1. Calculate edit distance between new review and each existing review
2. Normalize: similarity = 1 - (editDistance / max(len(review1), len(review2)))
3. Return max similarity score
```

---

### Threshold Configuration

**Configurable Thresholds in `application.properties`:**

```properties
# Review evaluation configuration
review.evaluation.enabled=true

# Similarity thresholds (0.0 - 1.0)
review.evaluation.threshold.approved=0.60
review.evaluation.threshold.moderation=0.85

# Interpretation:
# - similarityScore < 0.60 → APPROVED
# - 0.60 ≤ similarityScore < 0.85 → FOR_MODERATION  
# - similarityScore ≥ 0.85 → REJECTED

# Algorithm weights
review.evaluation.weight.cosine=0.7
review.evaluation.weight.levenshtein=0.3

# Performance optimization
review.evaluation.max.comparisons=1000
review.evaluation.cache.enabled=true
review.evaluation.cache.ttl=3600
```

**Recommended Tuning:**
- Start conservative (higher thresholds to avoid false rejections)
- Monitor false positive/negative rates
- Adjust thresholds based on production data after 2-4 weeks

---

## Implementation Tasks

### Phase 1: Foundation (Week 1)

**Task 1.1: Data Model Updates**
- [ ] Add `ReviewStatus` enum
- [ ] Update `Review` entity with new fields
- [ ] Create database migration script
- [ ] Test migration on local Postgres instance
- **Estimated Effort:** 4 hours
- **Acceptance Criteria:**
  - Migration runs without errors
  - Existing reviews have status = APPROVED
  - New columns are nullable/have defaults

**Task 1.2: Configuration Setup**
- [ ] Add evaluation properties to `application.properties`
- [ ] Create `ReviewEvaluationConfig` configuration bean
- [ ] Add feature flag for enabling/disabling evaluation
- **Estimated Effort:** 2 hours
- **Acceptance Criteria:**
  - Config bean loads thresholds correctly
  - Feature flag can disable evaluation entirely

**Task 1.3: Domain Models**
- [ ] Create `EvaluationResult` class
- [ ] Create `SimilarityCalculator` interface
- [ ] Update `Review` DTOs (request/response objects)
- **Estimated Effort:** 3 hours
- **Acceptance Criteria:**
  - All models have proper validation annotations
  - JSON serialization works correctly

---

### Phase 2: Core Logic (Week 1-2)

**Task 2.1: Similarity Calculator Implementations**
- [ ] Implement `TfIdfCosineSimilarityCalculator`
  - [ ] Text preprocessing (tokenization, stemming)
  - [ ] TF-IDF vector calculation
  - [ ] Cosine similarity computation
- [ ] Implement `LevenshteinSimilarityCalculator`
  - [ ] Dynamic programming implementation
  - [ ] Normalization logic
- [ ] Implement `CompositeSimilarityCalculator`
  - [ ] Weighted average logic
  - [ ] Configurable weight injection
- **Estimated Effort:** 12 hours
- **Acceptance Criteria:**
  - Unit tests pass with known similarity scores
  - Performance < 10ms per comparison
  - Handles edge cases (empty strings, identical strings)

**Task 2.2: Review Evaluation Service**
- [ ] Create `ReviewEvaluationService` class
- [ ] Implement `evaluate(Review, List<Review>)` method
  - [ ] Fetch existing reviews for product
  - [ ] Calculate similarity against each existing review
  - [ ] Determine max similarity score
  - [ ] Map score to ReviewStatus
  - [ ] Build EvaluationResult
- [ ] Add structured logging with `LogPublisher`
- **Estimated Effort:** 8 hours
- **Acceptance Criteria:**
  - Correctly classifies reviews based on thresholds
  - Logs include similarity scores and decisions
  - Handles empty existing review list (auto-approve)

**Task 2.3: Integration with ReviewService**
- [ ] Update `ReviewService.createReview()` to call evaluation
- [ ] Set review status based on EvaluationResult
- [ ] Populate evaluation metadata fields
- [ ] Handle evaluation failures gracefully (default to FOR_MODERATION)
- **Estimated Effort:** 4 hours
- **Acceptance Criteria:**
  - New reviews are evaluated before save
  - Metadata fields are populated
  - Feature flag can bypass evaluation

---

### Phase 3: API Updates (Week 2)

**Task 3.1: Update ReviewController**
- [ ] Modify POST /api/reviews response to include status fields
- [ ] Add status query parameter to GET endpoints
- [ ] Implement status filtering logic
- [ ] Update error handling for evaluation failures
- **Estimated Effort:** 4 hours
- **Acceptance Criteria:**
  - API responses include new fields
  - Filtering by status works correctly
  - Backward compatibility maintained (default filter = APPROVED)

**Task 3.2: Admin Moderation Endpoint**
- [ ] Create PATCH /api/reviews/{id}/status endpoint
- [ ] Add request validation
- [ ] Add moderation audit fields to Review entity
- [ ] Implement update logic in ReviewService
- **Estimated Effort:** 6 hours
- **Acceptance Criteria:**
  - Admins can change review status
  - Audit trail captures who/when
  - Invalid status transitions are rejected

**Task 3.3: Update ReviewRepository**
- [ ] Add `findByProductIdAndStatus()` query method
- [ ] Add `findByStatus()` query method
- [ ] Optimize queries with proper indexing
- **Estimated Effort:** 2 hours
- **Acceptance Criteria:**
  - Query methods return correct results
  - Indexes are used (verify with EXPLAIN)

---

### Phase 4: Testing (Week 2-3)

**Task 4.1: Unit Tests**
- [ ] SimilarityCalculator tests (all implementations)
- [ ] ReviewEvaluationService tests
- [ ] ReviewService integration tests
- [ ] Controller tests with MockMvc
- **Estimated Effort:** 12 hours
- **Coverage Target:** > 85% code coverage

**Task 4.2: Integration Tests**
- [ ] End-to-end review submission flow
- [ ] Test all three classification outcomes
- [ ] Test concurrent review submissions
- [ ] Test evaluation with large review sets (performance)
- **Estimated Effort:** 8 hours
- **Acceptance Criteria:**
  - All scenarios pass
  - Performance requirements met (< 500ms p95)

**Task 4.3: Manual Testing**
- [ ] Test via Postman/curl against local stack
- [ ] Verify RabbitMQ/Kafka logging integration still works
- [ ] Test database migration on fresh DB
- [ ] Test backward compatibility (old clients)
- **Estimated Effort:** 4 hours

---

### Phase 5: Observability & Documentation (Week 3)

**Task 5.1: Structured Logging**
- [ ] Add log events for evaluation start/completion
- [ ] Log similarity scores and status decisions
- [ ] Log evaluation failures with stack traces
- [ ] Include reviewId, productId, correlationId in metadata
- **Estimated Effort:** 3 hours
- **Acceptance Criteria:**
  - All evaluation steps have corresponding log entries
  - Logs are consumable by logging-service

**Task 5.2: Metrics (Future — Placeholder)**
- [ ] Add Micrometer metrics for evaluation latency
- [ ] Add counters for APPROVED/FOR_MODERATION/REJECTED
- [ ] Add gauge for average similarity score
- **Estimated Effort:** 4 hours (deferred to future sprint)

**Task 5.3: Documentation**
- [ ] Update CODEMAP.md with evaluation service
- [ ] Update service-topology.md (no new message flows)
- [ ] Create runbook for threshold tuning
- [ ] Document API changes in OpenAPI/Swagger (if available)
- **Estimated Effort:** 4 hours

---

### Phase 6: Deployment (Week 3)

**Task 6.1: Deployment Preparation**
- [ ] Create feature flag plan (evaluation disabled initially)
- [ ] Prepare rollback plan
- [ ] Create smoke test script
- **Estimated Effort:** 3 hours

**Task 6.2: Database Migration**
- [ ] Run migration on staging DB
- [ ] Verify zero downtime
- [ ] Validate existing reviews have status = APPROVED
- **Estimated Effort:** 2 hours

**Task 6.3: Service Deployment**
- [ ] Deploy review-service with evaluation disabled
- [ ] Run smoke tests
- [ ] Enable evaluation via feature flag
- [ ] Monitor logs and metrics
- **Estimated Effort:** 4 hours

---

## Testing Strategy

### Unit Tests

**SimilarityCalculator Tests:**
```java
@Test
void testIdenticalStrings_returnsSimilarityOne() {
    double similarity = calculator.calculateSimilarity("test", "test");
    assertEquals(1.0, similarity, 0.001);
}

@Test
void testCompletelyDifferentStrings_returnsSimilarityZero() {
    double similarity = calculator.calculateSimilarity("abc", "xyz");
    assertTrue(similarity < 0.1);
}

@Test
void testMinorTypos_returnsHighSimilarity() {
    double similarity = calculator.calculateSimilarity("great product", "great produkt");
    assertTrue(similarity > 0.85);
}
```

**ReviewEvaluationService Tests:**
```java
@Test
void testEvaluate_noExistingReviews_returnsApproved() {
    Review newReview = createReview("Unique comment");
    EvaluationResult result = service.evaluate(newReview, Collections.emptyList());
    assertEquals(ReviewStatus.APPROVED, result.getStatus());
}

@Test
void testEvaluate_highSimilarity_returnsRejected() {
    Review newReview = createReview("Great product!");
    Review existing = createReview("Great product!");
    
    EvaluationResult result = service.evaluate(newReview, List.of(existing));
    assertEquals(ReviewStatus.REJECTED, result.getStatus());
    assertTrue(result.getMaxSimilarityScore() > 0.85);
}
```

---

### Integration Tests

**End-to-End Review Submission:**
```java
@SpringBootTest
@AutoConfigureMockMvc
class ReviewEvaluationIntegrationTest {
    
    @Test
    void testCreateReview_uniqueComment_approved() throws Exception {
        mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"rating\":5,\"comment\":\"Unique review text\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
    
    @Test
    void testCreateReview_duplicateComment_rejected() throws Exception {
        // First review (approved)
        mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"rating\":5,\"comment\":\"Test review\"}"))
                .andExpect(status().isCreated());
        
        // Duplicate review (rejected)
        mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"rating\":5,\"comment\":\"Test review\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.similarityScore").exists());
    }
}
```

---

### Performance Tests

**Benchmark Test:**
```java
@Test
void testEvaluationPerformance_1000Reviews_completes200ms() {
    List<Review> existingReviews = createRandomReviews(1000);
    Review newReview = createReview("Test performance");
    
    long startTime = System.currentTimeMillis();
    service.evaluate(newReview, existingReviews);
    long duration = System.currentTimeMillis() - startTime;
    
    assertTrue(duration < 200, "Evaluation took " + duration + "ms");
}
```

---

### Manual Testing Scenarios

**Scenario 1: First Review for Product (Auto-Approve)**
```bash
curl -X POST http://localhost:8082/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"productId":999,"rating":5,"comment":"First review ever!"}'

# Expected: status=APPROVED, similarityScore=0.0 or null
```

**Scenario 2: Near-Duplicate Review (Reject)**
```bash
# Create first review
curl -X POST http://localhost:8082/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"productId":999,"rating":5,"comment":"Great product! Fast shipping."}'

# Submit near-duplicate
curl -X POST http://localhost:8082/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"productId":999,"rating":5,"comment":"Great product! Fast shipping."}'

# Expected: status=REJECTED, similarityScore >= 0.85
```

**Scenario 3: Similar but Different Review (Moderation)**
```bash
curl -X POST http://localhost:8082/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"productId":999,"rating":5,"comment":"Excellent product with quick delivery and top quality."}'

# Expected: status=FOR_MODERATION, 0.60 <= similarityScore < 0.85
```

---

## Configuration & Tuning

### Application Properties

**File:** `services/review-service/src/main/resources/application.properties`

```properties
# ========================================
# Review Evaluation Configuration
# ========================================

# Master toggle for evaluation feature
review.evaluation.enabled=true

# Similarity score thresholds (0.0 - 1.0)
# Lower threshold = more reviews approved automatically
# Higher threshold = more reviews require moderation
review.evaluation.threshold.approved=0.60
review.evaluation.threshold.moderation=0.85

# Algorithm configuration
# Cosine similarity weight (TF-IDF based)
review.evaluation.weight.cosine=0.7

# Levenshtein similarity weight (edit distance based)
review.evaluation.weight.levenshtein=0.3

# Performance tuning
# Maximum number of existing reviews to compare against
# If product has more reviews, only compare against most recent N
review.evaluation.max.comparisons=1000

# Enable caching of TF-IDF vectors for frequently accessed products
review.evaluation.cache.enabled=true
review.evaluation.cache.ttl=3600

# Text preprocessing
review.evaluation.text.lowercase=true
review.evaluation.text.remove.punctuation=true
review.evaluation.text.remove.stopwords=false
review.evaluation.text.stemming=false

# Logging
review.evaluation.logging.verbose=false
```

---

### Threshold Tuning Guide

**Initial Conservative Settings (Recommended):**
```properties
review.evaluation.threshold.approved=0.70
review.evaluation.threshold.moderation=0.90
```
- **Rationale:** Start strict to avoid false rejections
- **Expected:** More reviews go to FOR_MODERATION queue
- **Action:** Monitor moderation queue size and adjust

**Aggressive Spam Filtering:**
```properties
review.evaluation.threshold.approved=0.50
review.evaluation.threshold.moderation=0.80
```
- **Rationale:** Catch more duplicates automatically
- **Risk:** Higher false positive rate (legitimate reviews rejected)
- **Use Case:** High spam volume, dedicated moderation team

**Balanced Production Settings (After Tuning):**
```properties
review.evaluation.threshold.approved=0.60
review.evaluation.threshold.moderation=0.85
```
- **Rationale:** Based on production data analysis
- **Expected:** 70-80% auto-approved, 15-20% moderation, 5-10% rejected

---

## Deployment Plan

### Pre-Deployment Checklist

- [ ] All unit and integration tests pass (`mvn clean test`)
- [ ] Code review completed and approved
- [ ] Database migration script reviewed and tested on staging
- [ ] Feature flag configured (evaluation disabled initially)
- [ ] Rollback plan documented and tested
- [ ] Monitoring dashboards configured (logs, metrics)
- [ ] Communication sent to stakeholders (deployment time, expected behavior)

---

### Deployment Steps

**Step 1: Database Migration (Zero-Downtime)**

```bash
# Connect to staging database
psql -h localhost -U postgres -d reviewsdb

# Run migration script
\i services/review-service/src/main/resources/db/migration/V2__add_review_evaluation_fields.sql

# Verify migration
SELECT column_name, data_type, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_name = 'reviews';

# Expected: status, similarity_score, most_similar_review_id, evaluation_reason, evaluated_at columns exist
```

**Step 2: Deploy Application (Evaluation Disabled)**

```bash
# Build new version
cd services/review-service
mvn clean package -DskipTests

# Update docker-compose.yml or Kubernetes deployment with new image

# Set environment variable to disable evaluation
export REVIEW_EVALUATION_ENABLED=false

# Deploy service
docker-compose up -d review-service

# Verify service health
curl http://localhost:8082/actuator/health
```

**Step 3: Smoke Test (Backward Compatibility)**

```bash
# Submit review (should work as before, status=APPROVED by default)
curl -X POST http://localhost:8082/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"rating":5,"comment":"Test review"}'

# Verify response includes new fields
# Expected: status=APPROVED, similarityScore=null (evaluation disabled)

# Verify existing GET endpoints still work
curl http://localhost:8082/api/reviews/product/1
```

**Step 4: Enable Evaluation (Feature Flag)**

```bash
# Update configuration (hot reload if supported, or restart)
# Set REVIEW_EVALUATION_ENABLED=true in environment

# Restart service with evaluation enabled
docker-compose restart review-service

# Verify evaluation is active
curl -X POST http://localhost:8082/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"rating":5,"comment":"Another test review"}'

# Expected: response includes similarityScore > 0.0
```

**Step 5: Monitor and Validate**

```bash
# Check application logs
docker logs review-service -f | grep "Review evaluation"

# Expected log entries:
# - "Review evaluation started for productId=1"
# - "Calculated similarity score: 0.XX for reviewId=YY"
# - "Review classified as APPROVED/FOR_MODERATION/REJECTED"

# Monitor logging-service for structured logs
docker logs logging-service -f

# Check database for new review statuses
psql -h localhost -U postgres -d reviewsdb -c \
  "SELECT id, status, similarity_score, evaluation_reason FROM reviews ORDER BY id DESC LIMIT 10;"
```

---

### Rollback Plan

**If issues are detected:**

**Option 1: Disable Evaluation (Quick)**
```bash
export REVIEW_EVALUATION_ENABLED=false
docker-compose restart review-service
```
- **Impact:** New reviews default to APPROVED status
- **Downtime:** ~5 seconds (service restart)

**Option 2: Rollback to Previous Version (Full)**
```bash
# Revert to previous Docker image
docker-compose down review-service
docker-compose up -d review-service:previous-version

# Database rollback (if needed)
psql -h localhost -U postgres -d reviewsdb -c \
  "ALTER TABLE reviews DROP COLUMN status, 
   DROP COLUMN similarity_score, 
   DROP COLUMN most_similar_review_id, 
   DROP COLUMN evaluation_reason, 
   DROP COLUMN evaluated_at;"
```
- **Impact:** Lose evaluation data (acceptable if recent deployment)
- **Downtime:** ~30 seconds

---

### Post-Deployment Validation

**Success Criteria:**
- [ ] All existing GET endpoints return results (backward compatibility)
- [ ] New reviews are created with status field populated
- [ ] Similarity scores are calculated and logged
- [ ] No errors in application logs
- [ ] Database queries use indexes (verify with EXPLAIN)
- [ ] Response times < 500ms (p95)

**Monitoring Period:**
- Monitor closely for **first 2 hours** after enabling evaluation
- Review logs daily for **first week**
- Analyze classification distribution (APPROVED/FOR_MODERATION/REJECTED ratios)

---

## Monitoring & Observability

### Key Metrics to Track

**Application Metrics (via Micrometer — Future Enhancement):**

| Metric Name | Type | Description | Alert Threshold |
|-------------|------|-------------|-----------------|
| `review.evaluation.duration` | Timer | Time taken for evaluation | p95 > 200ms |
| `review.status.approved` | Counter | Count of auto-approved reviews | - |
| `review.status.moderation` | Counter | Count of reviews flagged for moderation | > 50% of total |
| `review.status.rejected` | Counter | Count of auto-rejected reviews | > 30% of total |
| `review.similarity.score.avg` | Gauge | Average similarity score | - |
| `review.evaluation.errors` | Counter | Evaluation failures | > 0 |

**Structured Logs (via logging-client):**

```json
{
  "message": "Review evaluation completed",
  "level": "INFO",
  "service": "review-service",
  "logger": "nik.kalomiris.review_service.evaluation.ReviewEvaluationService",
  "metadata": {
    "reviewId": "789",
    "productId": "123",
    "status": "FOR_MODERATION",
    "similarityScore": "0.72",
    "mostSimilarReviewId": "456",
    "evaluationDurationMs": "145",
    "existingReviewsCount": "87"
  }
}
```

**Database Monitoring:**

```sql
-- Daily status distribution
SELECT status, COUNT(*) as count, 
       ROUND(AVG(similarity_score), 2) as avg_similarity
FROM reviews
WHERE evaluated_at >= NOW() - INTERVAL '1 day'
GROUP BY status;

-- Top products with high rejection rates
SELECT product_id, 
       COUNT(*) FILTER (WHERE status = 'REJECTED') as rejected_count,
       COUNT(*) as total_count,
       ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'REJECTED') / COUNT(*), 2) as rejection_rate
FROM reviews
WHERE evaluated_at >= NOW() - INTERVAL '7 days'
GROUP BY product_id
HAVING COUNT(*) > 10
ORDER BY rejection_rate DESC
LIMIT 10;
```

---

### Alerting Rules

**Critical Alerts (PagerDuty / Slack):**

1. **Evaluation Error Rate > 5%**
   - **Trigger:** `review.evaluation.errors / total_reviews > 0.05` over 5 minutes
   - **Action:** Check logs for stack traces, verify database connectivity

2. **Response Time Degradation**
   - **Trigger:** `review.evaluation.duration (p95) > 500ms` over 5 minutes
   - **Action:** Check database query performance, review cache hit rate

3. **High Rejection Rate**
   - **Trigger:** `review.status.rejected / total_reviews > 0.5` over 1 hour
   - **Action:** Verify thresholds not misconfigured, check for spam attack

**Warning Alerts (Email / Slack):**

1. **Moderation Queue Buildup**
   - **Trigger:** `review.status.moderation / total_reviews > 0.3` over 24 hours
   - **Action:** Consider lowering thresholds, allocate moderation resources

2. **Cache Miss Rate High**
   - **Trigger:** `cache.misses / cache.requests > 0.8` over 1 hour
   - **Action:** Increase cache TTL or capacity

---

### Debugging Runbook

**Issue: All reviews being rejected**

**Symptoms:**
- High rejection rate (> 80%)
- Similarity scores consistently high

**Diagnosis:**
```bash
# Check threshold configuration
docker exec review-service env | grep REVIEW_EVALUATION

# Check recent evaluations
docker exec -it postgres-service psql -U postgres -d reviewsdb -c \
  "SELECT id, status, similarity_score, evaluation_reason FROM reviews ORDER BY id DESC LIMIT 20;"
```

**Resolution:**
- Verify thresholds are correct (not inverted)
- Check for configuration override in environment variables
- Review similarity calculator implementation for bugs

---

**Issue: Evaluation timeout / slow performance**

**Symptoms:**
- Requests taking > 1 second
- Timeout errors in logs

**Diagnosis:**
```bash
# Check number of existing reviews per product
docker exec -it postgres-service psql -U postgres -d reviewsdb -c \
  "SELECT product_id, COUNT(*) FROM reviews GROUP BY product_id ORDER BY COUNT(*) DESC LIMIT 10;"

# Check if indexes are being used
docker exec -it postgres-service psql -U postgres -d reviewsdb -c \
  "EXPLAIN ANALYZE SELECT * FROM reviews WHERE product_id = 123;"
```

**Resolution:**
- Lower `review.evaluation.max.comparisons` limit
- Enable caching (`review.evaluation.cache.enabled=true`)
- Add pagination for large review sets

---

## Risks & Mitigation

### Technical Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **False rejections** (legitimate reviews auto-rejected) | Medium | High | Start with conservative thresholds (0.70/0.90); monitor rejection reasons; provide admin override endpoint |
| **Performance degradation** on products with 1000+ reviews | Medium | Medium | Implement max comparison limit; cache TF-IDF vectors; add async evaluation option |
| **Database migration failure** | Low | High | Test migration on staging; use transactional DDL; create rollback script; schedule during low-traffic window |
| **Evaluation service crashes** break review submission | Low | Critical | Wrap evaluation in try-catch; default to FOR_MODERATION on failure; feature flag for quick disable |
| **Spam bots adapt** to bypass similarity detection | High | Medium | Monitor rejection rates; implement additional checks (rate limiting, CAPTCHA); use ML-based detection (future) |

### Business Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **Customer frustration** from rejected reviews | Medium | High | Clear user messaging ("Similar review already exists"); provide appeal mechanism; human moderation queue |
| **Increased moderation workload** initially | High | Medium | Tune thresholds after 2-4 weeks; provide moderation dashboard; batch review tools |
| **Compliance issues** (GDPR, user content rights) | Low | Medium | Store evaluation metadata separately; provide data export; allow user deletion of rejected reviews |

---

### Mitigation Strategies

**1. Gradual Rollout (Canary Deployment)**
- Deploy to 10% of users first (if infrastructure supports)
- Monitor metrics for 48 hours
- Gradually increase to 100%

**2. Manual Override**
- Admin endpoint to override status
- Audit trail for all overrides
- User appeal workflow (future enhancement)

**3. Automated Threshold Tuning**
- Weekly analysis of classification distribution
- A/B testing of different threshold values
- ML-based threshold optimization (future)

**4. Fallback Behavior**
- On evaluation failure: default to FOR_MODERATION (safe option)
- On database failure: return 503 (don't create review without evaluation)
- On cache failure: proceed without cache (degraded performance, not failure)

---

## Future Enhancements

### Short-Term (Next Sprint)

**1. Advanced Similarity Algorithms**
- Implement semantic similarity using embeddings (Sentence-BERT)
- Add language detection and multilingual support
- Implement n-gram analysis for short reviews

**2. Performance Optimizations**
- Async evaluation with message queue (RabbitMQ)
- Batch evaluation for bulk imports
- Distributed caching (Redis)

**3. Admin Dashboard**
- Moderation queue UI
- Bulk approve/reject actions
- Analytics dashboard (rejection rates, top flagged products)

### Medium-Term (Next Quarter)

**4. Machine Learning Integration**
- Train spam detection model (classification: spam vs. legitimate)
- Sentiment analysis to detect fake positive/negative reviews
- Anomalous review detection (sudden surge in 5-star reviews)

**5. User-Facing Features**
- User notification on review rejection with reason
- Appeal/dispute mechanism
- Review edit/resubmit workflow

**6. Cross-Product Analysis**
- Detect users submitting identical reviews across multiple products
- User reputation score based on review history
- Collaborative filtering for spam detection

### Long-Term (6-12 Months)

**7. Real-Time Review Network Analysis**
- Graph database integration (Neo4j) to detect review rings
- Social network analysis of reviewers
- Time-series anomaly detection

**8. Federated Learning**
- Share spam patterns across multiple e-commerce platforms (privacy-preserving)
- Community-driven spam signature database

**9. Advanced NLP**
- Aspect-based sentiment analysis (product quality vs. shipping vs. service)
- Sarcasm and irony detection
- Context-aware similarity (same words, different meaning)

---

## Appendix

### A. Sample Review Similarity Scores

| Review 1 | Review 2 | Cosine Similarity | Levenshtein Similarity | Final Score | Classification |
|----------|----------|-------------------|------------------------|-------------|----------------|
| "Great product!" | "Great product!" | 1.00 | 1.00 | 1.00 | REJECTED |
| "Great product!" | "Great produkt!" | 0.95 | 0.93 | 0.94 | REJECTED |
| "Great product! Fast shipping." | "Great product! Quick delivery." | 0.78 | 0.65 | 0.74 | FOR_MODERATION |
| "Great product!" | "Terrible product!" | 0.50 | 0.50 | 0.50 | APPROVED |
| "Excellent laptop with fast processor" | "Amazing phone with great camera" | 0.35 | 0.20 | 0.31 | APPROVED |

---

### B. Database Schema Reference

**Full Review Table Schema (After Migration):**

```sql
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL,
    upvotes INTEGER DEFAULT 0,
    downvotes INTEGER DEFAULT 0,
    
    -- Evaluation fields
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    similarity_score DECIMAL(5,4),
    most_similar_review_id BIGINT,
    evaluation_reason VARCHAR(500),
    evaluated_at TIMESTAMP,
    
    -- Moderation fields (future)
    moderated_by VARCHAR(100),
    moderated_at TIMESTAMP,
    moderation_note TEXT,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT fk_most_similar_review 
        FOREIGN KEY (most_similar_review_id) 
        REFERENCES reviews(id) 
        ON DELETE SET NULL
);

-- Indexes
CREATE INDEX idx_reviews_product_id ON reviews(product_id);
CREATE INDEX idx_reviews_status ON reviews(status);
CREATE INDEX idx_reviews_product_status ON reviews(product_id, status);
CREATE INDEX idx_reviews_evaluated_at ON reviews(evaluated_at);
```

---

### C. Code Snippets

**ReviewEvaluationService (Simplified):**

```java
@Service
public class ReviewEvaluationService {
    
    private final SimilarityCalculator similarityCalculator;
    private final ReviewEvaluationConfig config;
    private final LogPublisher logPublisher;
    
    public EvaluationResult evaluate(Review newReview, List<Review> existingReviews) {
        if (existingReviews.isEmpty()) {
            return EvaluationResult.approved(0.0, "First review for product");
        }
        
        double maxSimilarity = 0.0;
        Long mostSimilarId = null;
        
        for (Review existing : existingReviews) {
            double similarity = similarityCalculator.calculateSimilarity(
                newReview.getComment(), 
                existing.getComment()
            );
            
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                mostSimilarId = existing.getId();
            }
        }
        
        ReviewStatus status = determineStatus(maxSimilarity);
        String reason = buildEvaluationReason(status, maxSimilarity, mostSimilarId);
        
        logEvaluation(newReview, status, maxSimilarity, existingReviews.size());
        
        return EvaluationResult.builder()
            .status(status)
            .maxSimilarityScore(maxSimilarity)
            .mostSimilarReviewId(mostSimilarId)
            .evaluationReason(reason)
            .evaluatedAt(Instant.now())
            .build();
    }
    
    private ReviewStatus determineStatus(double similarityScore) {
        if (similarityScore >= config.getModerationThreshold()) {
            return ReviewStatus.REJECTED;
        } else if (similarityScore >= config.getApprovedThreshold()) {
            return ReviewStatus.FOR_MODERATION;
        } else {
            return ReviewStatus.APPROVED;
        }
    }
}
```

---

### D. Testing Checklist

**Pre-Deployment Testing:**
- [ ] Unit tests: all pass (> 85% coverage)
- [ ] Integration tests: all scenarios covered
- [ ] Performance tests: evaluation < 200ms for 1000 reviews
- [ ] Load tests: concurrent submissions (100 req/s)
- [ ] Database migration: tested on staging
- [ ] Backward compatibility: old clients still work
- [ ] Feature flag: enable/disable works correctly
- [ ] Rollback: tested and documented

**Post-Deployment Validation:**
- [ ] Smoke tests: basic flows work
- [ ] Monitoring: logs and metrics flowing
- [ ] Status distribution: reasonable ratios (not all rejected/approved)
- [ ] Performance: response times within SLA
- [ ] User feedback: no complaints about false rejections

---

## Summary

This implementation plan provides a comprehensive roadmap for building a review evaluation utility based on text similarity detection. The system will automatically classify reviews as APPROVED, FOR_MODERATION, or REJECTED before saving to the database, reducing spam and duplicate content while maintaining a positive user experience.

**Key Success Factors:**
1. **Conservative thresholds** to avoid false rejections
2. **Comprehensive testing** at all levels (unit, integration, performance)
3. **Gradual rollout** with feature flags and monitoring
4. **Clear documentation** for troubleshooting and tuning
5. **Fallback mechanisms** for graceful degradation

**Estimated Timeline:** 3 weeks (foundation + core logic + testing + deployment)

**Next Steps:**
1. Review and approve this plan with stakeholders
2. Create JIRA tickets for each task in Phases 1-6
3. Allocate development resources
4. Begin Phase 1 (Foundation) implementation

---

**Document Owner:** Development Team  
**Reviewers:** Product Manager, Engineering Lead, QA Lead  
**Approval Date:** [Pending]  
**Last Updated:** 2025-11-09
