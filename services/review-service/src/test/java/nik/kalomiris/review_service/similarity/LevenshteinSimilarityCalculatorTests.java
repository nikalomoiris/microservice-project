package nik.kalomiris.review_service.similarity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevenshteinSimilarityCalculatorTests {

    @Test
    void identicalStringsReturnOne() {
        LevenshteinSimilarityCalculator calc = new LevenshteinSimilarityCalculator();
        assertEquals(1.0, calc.similarity("abc", "abc"), 1e-9);
    }

    @Test
    void shortDifferencesProduceHighSimilarity() {
        LevenshteinSimilarityCalculator calc = new LevenshteinSimilarityCalculator();
        // "hello" vs "helo" -> one deletion -> similarity should be high
        double s = calc.similarity("hello", "helo");
        // basic assertion; adjust threshold when tuning algorithm
        assertEquals(true, s > 0.6);
    }
}
