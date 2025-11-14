package nik.kalomiris.review_service.similarity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
