package nik.kalomiris.review_service.review;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "reviews")
public class Review {
    /**
     * Entity representing a product review. Includes rating, text comment
     * and simple upvote/downvote counters used by the UI.
     */

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private int rating;
    private String comment;
    private Integer upvotes = 0;
    private Integer downvotes = 0;
    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.FOR_MODERATION;

    // Evaluation metadata fields
    private Double similarityScore;
    private Long mostSimilarReviewId;
    private String evaluationReason;
    private java.time.Instant evaluatedAt;

    public Review() {
        // Default constructor required by JPA
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getUpvotes() {
        return upvotes;
    }

    public void setUpvotes(Integer upvotes) {
        this.upvotes = upvotes;
    }

    public Integer getDownvotes() {
        return downvotes;
    }

    public void setDownvotes(Integer downvotes) {
        this.downvotes = downvotes;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Long getMostSimilarReviewId() {
        return mostSimilarReviewId;
    }

    public void setMostSimilarReviewId(Long mostSimilarReviewId) {
        this.mostSimilarReviewId = mostSimilarReviewId;
    }

    public String getEvaluationReason() {
        return evaluationReason;
    }

    public void setEvaluationReason(String evaluationReason) {
        this.evaluationReason = evaluationReason;
    }

    public java.time.Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(java.time.Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
