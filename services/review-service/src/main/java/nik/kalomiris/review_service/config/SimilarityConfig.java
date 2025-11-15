package nik.kalomiris.review_service.config;

import nik.kalomiris.review_service.similarity.CompositeSimilarityCalculator;
import nik.kalomiris.review_service.similarity.LevenshteinSimilarityCalculator;
import nik.kalomiris.review_service.similarity.SimilarityCalculator;
import nik.kalomiris.review_service.similarity.TfidfCosineSimilarityCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for similarity calculation components.
 * 
 * Wires up the similarity calculators used by ReviewEvaluationService:
 * - TF-IDF Cosine Similarity (primary algorithm)
 * - Levenshtein Distance (secondary algorithm)
 * - Composite calculator (weighted combination of both)
 * 
 * Weights are configured via ReviewEvaluationConfig and externalized
 * to application.properties for easy tuning.
 */
@Configuration
public class SimilarityConfig {

    /**
     * Create the composite similarity calculator bean.
     * 
     * This calculator combines TF-IDF cosine similarity and Levenshtein
     * similarity using weights from ReviewEvaluationConfig:
     * - Default: 70% TF-IDF, 30% Levenshtein
     * 
     * The composite approach balances:
     * - TF-IDF: Good for semantic similarity, handles word importance
     * - Levenshtein: Good for character-level edits, catches typos
     * 
     * @param config evaluation configuration with algorithm weights
     * @return configured composite similarity calculator
     */
    @Bean
    public SimilarityCalculator similarityCalculator(ReviewEvaluationConfig config) {
        // Create individual calculators
        SimilarityCalculator tfidfCalculator = new TfidfCosineSimilarityCalculator();
        SimilarityCalculator levenshteinCalculator = new LevenshteinSimilarityCalculator();

        // Get weights from config
        double cosineWeight = config.getWeight().getCosine();
        double levenshteinWeight = config.getWeight().getLevenshtein();

        // Validate weights (should sum to ~1.0, enforced by config validation)
        if (!config.getWeight().isValid()) {
            throw new IllegalStateException(
                    "Invalid similarity weights: cosine=" + cosineWeight +
                            ", levenshtein=" + levenshteinWeight +
                            " (should sum to 1.0)");
        }

        // Create and return composite calculator
        return new CompositeSimilarityCalculator(
                tfidfCalculator,
                levenshteinCalculator,
                cosineWeight,
                levenshteinWeight);
    }
}
