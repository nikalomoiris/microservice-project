package nik.kalomiris.review_service.similarity;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nik.kalomiris.review_service.review.ReviewStatus;

/**
 * Immutable domain object that represents the result of evaluating a review for
 * similarity/spam. Instances are created via the {@link Builder} or
 * deserialized
 * by Jackson using the
 * {@link #EvaluationResult(Long, Long, Double, Long, String, java.time.Instant, nik.kalomiris.review_service.review.ReviewStatus)}
 * constructor.
 *
 * <p>
 * Fields are annotated with Jakarta Validation constraints so callers can
 * validate instances using a {@code Validator} if desired. The {@link Builder}
 * performs fast-fail checks as well.
 * </p>
 */
public final class EvaluationResult {

    @NotNull
    @JsonProperty("reviewId")
    private final Long reviewId;

    @NotNull
    @JsonProperty("productId")
    private final Long productId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "1.0", inclusive = true)
    @JsonProperty("similarityScore")
    private final Double similarityScore;

    @JsonProperty("mostSimilarReviewId")
    private final Long mostSimilarReviewId;

    @Size(max = 500)
    @JsonProperty("evaluationReason")
    private final String evaluationReason;

    @NotNull
    @JsonProperty("evaluatedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant evaluatedAt;

    @NotNull
    @JsonProperty("status")
    private final ReviewStatus status;

    /**
     * All-arguments constructor used by the {@link Builder} and by Jackson during
     * deserialization. Parameters are annotated with {@link JsonProperty} to ensure
     * stable mapping between JSON and constructor arguments.
     *
     * @param reviewId            id of the evaluated review (required)
     * @param productId           id of the product the review belongs to (required)
     * @param similarityScore     highest similarity score found (0.0 - 1.0,
     *                            required)
     * @param mostSimilarReviewId id of the most similar existing review (nullable)
     * @param evaluationReason    short explanation for the decision (nullable, max
     *                            500 chars)
     * @param evaluatedAt         instant when the evaluation was performed
     *                            (required)
     * @param status              resulting {@link ReviewStatus} classification
     *                            (required)
     */
    @JsonCreator
    public EvaluationResult(
            @JsonProperty("reviewId") Long reviewId,
            @JsonProperty("productId") Long productId,
            @JsonProperty("similarityScore") Double similarityScore,
            @JsonProperty("mostSimilarReviewId") Long mostSimilarReviewId,
            @JsonProperty("evaluationReason") String evaluationReason,
            @JsonProperty("evaluatedAt") Instant evaluatedAt,
            @JsonProperty("status") ReviewStatus status) {
        this.reviewId = reviewId;
        this.productId = productId;
        this.similarityScore = similarityScore;
        this.mostSimilarReviewId = mostSimilarReviewId;
        this.evaluationReason = evaluationReason;
        this.evaluatedAt = evaluatedAt;
        this.status = status;
    }

    // Getters

    public Long getReviewId() {
        return reviewId;
    }

    public Long getProductId() {
        return productId;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public Long getMostSimilarReviewId() {
        return mostSimilarReviewId;
    }

    public String getEvaluationReason() {
        return evaluationReason;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    // Builder pattern for easier instantiation
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long reviewId;
        private Long productId;
        private Double similarityScore;
        private Long mostSimilarReviewId;
        private String evaluationReason;
        private Instant evaluatedAt;
        private ReviewStatus status;

        private Builder() {
        }

        public Builder reviewId(Long reviewId) {
            this.reviewId = reviewId;
            return this;
        }

        public Builder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        public Builder similarityScore(Double similarityScore) {
            this.similarityScore = similarityScore;
            return this;
        }

        public Builder mostSimilarReviewId(Long mostSimilarReviewId) {
            this.mostSimilarReviewId = mostSimilarReviewId;
            return this;
        }

        public Builder evaluationReason(String evaluationReason) {
            this.evaluationReason = evaluationReason;
            return this;
        }

        public Builder evaluatedAt(Instant evaluatedAt) {
            this.evaluatedAt = evaluatedAt;
            return this;
        }

        public Builder status(ReviewStatus status) {
            this.status = status;
            return this;
        }

        public EvaluationResult build() {
            if (reviewId == null)
                throw new IllegalStateException("reviewId cannot be null");
            if (productId == null)
                throw new IllegalStateException("productId cannot be null");
            if (similarityScore == null)
                throw new IllegalStateException("similarityScore cannot be null");
            if (similarityScore < 0.0 || similarityScore > 1.0)
                throw new IllegalStateException("similarityScore must be between 0.0 and 1.0");
            if (evaluatedAt == null)
                this.evaluatedAt = Instant.now();
            if (status == null)
                throw new IllegalStateException("status cannot be null");
            if (evaluationReason != null && evaluationReason.length() > 500)
                throw new IllegalStateException("evaluationReason cannot exceed 500 characters");
            return new EvaluationResult(
                    reviewId,
                    productId,
                    similarityScore,
                    mostSimilarReviewId,
                    evaluationReason,
                    evaluatedAt,
                    status);
        }
    }

    /**
     * Human readable representation useful for logging and tests.
     */
    @Override
    public String toString() {
        return "EvaluationResult{" +
                "reviewId=" + reviewId +
                ", productId=" + productId +
                ", similarityScore=" + similarityScore +
                ", mostSimilarReviewId=" + mostSimilarReviewId +
                ", evaluationReason='" + evaluationReason + '\'' +
                ", evaluatedAt=" + evaluatedAt +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        EvaluationResult that = (EvaluationResult) o;

        if (!reviewId.equals(that.reviewId))
            return false;
        if (!productId.equals(that.productId))
            return false;
        if (!similarityScore.equals(that.similarityScore))
            return false;
        if (mostSimilarReviewId != null ? !mostSimilarReviewId.equals(that.mostSimilarReviewId)
                : that.mostSimilarReviewId != null)
            return false;
        if (evaluationReason != null ? !evaluationReason.equals(that.evaluationReason) : that.evaluationReason != null)
            return false;
        if (!evaluatedAt.equals(that.evaluatedAt))
            return false;
        return status == that.status;
    }

    @Override
    public int hashCode() {
        int result = reviewId.hashCode();
        result = 31 * result + productId.hashCode();
        result = 31 * result + similarityScore.hashCode();
        result = 31 * result + (mostSimilarReviewId != null ? mostSimilarReviewId.hashCode() : 0);
        result = 31 * result + (evaluationReason != null ? evaluationReason.hashCode() : 0);
        result = 31 * result + evaluatedAt.hashCode();
        result = 31 * result + status.hashCode();
        return result;
    }

}
