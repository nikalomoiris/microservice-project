package nik.kalomiris.review_service.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for review similarity evaluation.
 * 
 * This configuration controls the behavior of the review evaluation system,
 * including similarity thresholds, algorithm weights, and performance settings.
 */
@Configuration
@ConfigurationProperties(prefix = "review.evaluation")
@Validated
public class ReviewEvaluationConfig {

    /**
     * Feature flag to enable/disable review evaluation entirely.
     * When disabled, all reviews are auto-approved without similarity checks.
     */
    private boolean enabled = true;

    /**
     * Thresholds for determining review status based on similarity scores.
     */
    private Threshold threshold = new Threshold();

    /**
     * Algorithm weights for composite similarity calculation.
     */
    private Weight weight = new Weight();

    /**
     * Maximum number of existing reviews to compare against.
     * Limits performance impact for products with many reviews.
     */
    @Min(1)
    @Max(10000)
    private int maxComparisons = 1000;

    /**
     * Cache settings for similarity calculations.
     */
    private Cache cache = new Cache();

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Threshold getThreshold() {
        return threshold;
    }

    public void setThreshold(Threshold threshold) {
        this.threshold = threshold;
    }

    public Weight getWeight() {
        return weight;
    }

    public void setWeight(Weight weight) {
        this.weight = weight;
    }

    public int getMaxComparisons() {
        return maxComparisons;
    }

    public void setMaxComparisons(int maxComparisons) {
        this.maxComparisons = maxComparisons;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * Threshold configuration for similarity score classification.
     * 
     * Interpretation:
     * - similarityScore < approved → APPROVED
     * - approved ≤ similarityScore < moderation → FOR_MODERATION
     * - similarityScore ≥ moderation → REJECTED
     */
    public static class Threshold {
        /**
         * Similarity threshold below which reviews are auto-approved.
         * Default: 0.60 (60% similarity)
         */
        @DecimalMin(value = "0.0", message = "Approved threshold must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Approved threshold must be at most 1.0")
        private double approved = 0.60;

        /**
         * Similarity threshold above which reviews are flagged for moderation.
         * Default: 0.85 (85% similarity)
         */
        @DecimalMin(value = "0.0", message = "Moderation threshold must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Moderation threshold must be at most 1.0")
        private double moderation = 0.85;

        public double getApproved() {
            return approved;
        }

        public void setApproved(double approved) {
            this.approved = approved;
        }

        public double getModeration() {
            return moderation;
        }

        public void setModeration(double moderation) {
            this.moderation = moderation;
        }

        /**
         * Validates that approved threshold is less than moderation threshold.
         */
        public boolean isValid() {
            return approved < moderation;
        }
    }

    /**
     * Weight configuration for combining multiple similarity algorithms.
     * Weights should sum to 1.0 for proper weighted average calculation.
     */
    public static class Weight {
        /**
         * Weight for TF-IDF cosine similarity algorithm.
         * Default: 0.7 (70% weight)
         */
        @DecimalMin(value = "0.0", message = "Cosine weight must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Cosine weight must be at most 1.0")
        private double cosine = 0.7;

        /**
         * Weight for Levenshtein similarity algorithm.
         * Default: 0.3 (30% weight)
         */
        @DecimalMin(value = "0.0", message = "Levenshtein weight must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Levenshtein weight must be at most 1.0")
        private double levenshtein = 0.3;

        public double getCosine() {
            return cosine;
        }

        public void setCosine(double cosine) {
            this.cosine = cosine;
        }

        public double getLevenshtein() {
            return levenshtein;
        }

        public void setLevenshtein(double levenshtein) {
            this.levenshtein = levenshtein;
        }

        /**
         * Validates that weights sum to approximately 1.0.
         * Allows small floating-point rounding errors (epsilon = 0.001).
         */
        public boolean isValid() {
            double sum = cosine + levenshtein;
            return Math.abs(sum - 1.0) < 0.001;
        }
    }

    /**
     * Cache configuration for similarity calculation optimization.
     */
    public static class Cache {
        /**
         * Enable/disable caching of similarity calculations.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Cache time-to-live in seconds.
         * Default: 3600 (1 hour)
         */
        @Min(value = 1, message = "Cache TTL must be at least 1 second")
        private int ttl = 3600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtl() {
            return ttl;
        }

        public void setTtl(int ttl) {
            this.ttl = ttl;
        }
    }

    /**
     * Validates the entire configuration for logical consistency.
     */
    public boolean isValid() {
        return threshold.isValid() && weight.isValid();
    }

    @Override
    public String toString() {
        return "ReviewEvaluationConfig{" +
                "enabled=" + enabled +
                ", threshold.approved=" + threshold.approved +
                ", threshold.moderation=" + threshold.moderation +
                ", weight.cosine=" + weight.cosine +
                ", weight.levenshtein=" + weight.levenshtein +
                ", maxComparisons=" + maxComparisons +
                ", cache.enabled=" + cache.enabled +
                ", cache.ttl=" + cache.ttl +
                '}';
    }
}
