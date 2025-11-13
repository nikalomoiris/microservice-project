package nik.kalomiris.review_service.similarity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Basic unit test skeletons for {@link TfidfCosineSimilarityCalculator}.
 *
 * Expand these tests with more cases (short texts, near-duplicates,
 * performance edge-cases) when implementing the full TF-IDF algorithm.
 */
class TfidfCosineSimilarityCalculatorTests {

    @Test
    void identicalStringsShouldReturnOne() {
        TfidfCosineSimilarityCalculator calc = new TfidfCosineSimilarityCalculator();
        double s = calc.similarity("Hello world", "Hello world");
        assertEquals(1.0, s, 1e-9, "Identical texts must have similarity 1.0");
    }

    @Test
    void nullsAreHandledAsEmptyStrings() {
        TfidfCosineSimilarityCalculator calc = new TfidfCosineSimilarityCalculator();
        double s = calc.similarity(null, null);
        assertEquals(1.0, s, 1e-9, "Two nulls should be treated as identical empty strings");
    }
}
