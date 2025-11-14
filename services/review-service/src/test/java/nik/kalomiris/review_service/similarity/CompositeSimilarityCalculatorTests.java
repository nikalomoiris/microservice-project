package nik.kalomiris.review_service.similarity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CompositeSimilarityCalculator}.
 * Tests weighted combination of multiple similarity algorithms.
 */
class CompositeSimilarityCalculatorTests {

    @Test
    void compositeCombinesWeightsCorrectly() {
        SimilarityCalculator c1 = new TfidfCosineSimilarityCalculator();
        SimilarityCalculator c2 = new LevenshteinSimilarityCalculator();
        CompositeSimilarityCalculator composite = new CompositeSimilarityCalculator(c1, c2, 0.7, 0.3);

        // for identical strings both calculators return 1.0 -> composite should be 1.0
        double s = composite.similarity("same text", "same text");
        assertEquals(1.0, s, 1e-9);
    }

    @Test
    void compositeWithDifferentWeights() {
        SimilarityCalculator cosine = new TfidfCosineSimilarityCalculator();
        SimilarityCalculator levenshtein = new LevenshteinSimilarityCalculator();
        
        // Test different weight combinations produce reasonable results
        CompositeSimilarityCalculator composite70_30 = new CompositeSimilarityCalculator(cosine, levenshtein, 0.7, 0.3);
        CompositeSimilarityCalculator composite50_50 = new CompositeSimilarityCalculator(cosine, levenshtein, 0.5, 0.5);

        String text1 = "Great product highly recommend";
        String text2 = "Great product I highly recommend";

        double sim1 = composite70_30.similarity(text1, text2);
        double sim2 = composite50_50.similarity(text1, text2);

        // All should give high similarity for similar texts
        assertTrue(sim1 > 0.7, "70/30 composite should give high similarity");
        assertTrue(sim2 > 0.7, "50/50 composite should give high similarity");
    }

    @Test
    void compositeHandlesEmptyStrings() {
        SimilarityCalculator c1 = new TfidfCosineSimilarityCalculator();
        SimilarityCalculator c2 = new LevenshteinSimilarityCalculator();
        CompositeSimilarityCalculator composite = new CompositeSimilarityCalculator(c1, c2, 0.7, 0.3);

        double sim1 = composite.similarity("", "");
        double sim2 = composite.similarity("", "text");

        assertEquals(1.0, sim1, 1e-9, "Two empty strings should be identical");
        assertEquals(0.0, sim2, 1e-9, "Empty vs non-empty should return 0");
    }

    @Test
    void compositeIsSymmetric() {
        SimilarityCalculator c1 = new TfidfCosineSimilarityCalculator();
        SimilarityCalculator c2 = new LevenshteinSimilarityCalculator();
        CompositeSimilarityCalculator composite = new CompositeSimilarityCalculator(c1, c2, 0.7, 0.3);

        String text1 = "Product A review";
        String text2 = "Product B review";

        double sim1 = composite.similarity(text1, text2);
        double sim2 = composite.similarity(text2, text1);

        assertEquals(sim1, sim2, 1e-9, "Similarity should be symmetric");
    }

    @Test
    void compositeProducesValueInValidRange() {
        SimilarityCalculator c1 = new TfidfCosineSimilarityCalculator();
        SimilarityCalculator c2 = new LevenshteinSimilarityCalculator();
        CompositeSimilarityCalculator composite = new CompositeSimilarityCalculator(c1, c2, 0.7, 0.3);

        String[] texts = {
            "Great product",
            "Terrible quality",
            "Fast shipping excellent service",
            "Would not recommend",
            "Amazing purchase best ever"
        };

        // Test all pairs
        for (String t1 : texts) {
            for (String t2 : texts) {
                double sim = composite.similarity(t1, t2);
                assertTrue(sim >= 0.0 && sim <= 1.0,
                        "Similarity must be in [0,1], got: " + sim);
            }
        }
    }
}

