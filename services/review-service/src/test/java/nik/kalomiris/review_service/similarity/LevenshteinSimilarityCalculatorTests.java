package nik.kalomiris.review_service.similarity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LevenshteinSimilarityCalculator}.
 * Tests Levenshtein distance calculation and similarity scoring.
 */
class LevenshteinSimilarityCalculatorTests {

    private final LevenshteinSimilarityCalculator calc = new LevenshteinSimilarityCalculator();

    @Test
    void identicalStringsReturnOne() {
        assertEquals(1.0, calc.similarity("abc", "abc"), 1e-9);
        assertEquals(1.0, calc.similarity("Hello World", "Hello World"), 1e-9);
    }

    @Test
    void emptyStringsReturnOne() {
        assertEquals(1.0, calc.similarity("", ""), 1e-9,
                "Two empty strings should be identical");
    }

    @Test
    void nullsAreTreatedAsEmptyStrings() {
        assertEquals(1.0, calc.similarity(null, null), 1e-9,
                "Two nulls should be treated as identical");
    }

    @Test
    void oneEmptyStringReturnsZero() {
        assertEquals(0.0, calc.similarity("", "abc"), 1e-9,
                "Empty vs non-empty should return 0");
        assertEquals(0.0, calc.similarity("abc", ""), 1e-9,
                "Non-empty vs empty should return 0");
    }

    @Test
    void oneNullReturnsZero() {
        assertEquals(0.0, calc.similarity(null, "abc"), 1e-9);
        assertEquals(0.0, calc.similarity("abc", null), 1e-9);
    }

    @Test
    void singleCharacterDifference() {
        // "hello" vs "helo" -> 1 deletion
        double s = calc.similarity("hello", "helo");
        assertTrue(s > 0.75, "Single char difference should have high similarity, got: " + s);
        assertTrue(s < 1.0, "Different strings should not have similarity 1.0");
    }

    @Test
    void shortDifferencesProduceHighSimilarity() {
        // "hello" vs "helo" -> one deletion
        double s = calc.similarity("hello", "helo");
        assertTrue(s > 0.6, "Single deletion should produce high similarity, got: " + s);
    }

    @Test
    void completelyDifferentStringsHaveLowSimilarity() {
        double s = calc.similarity("abc", "xyz");
        assertTrue(s < 0.3, "Completely different strings should have low similarity, got: " + s);
    }

    @Test
    void substringsShouldHaveModerateToHighSimilarity() {
        String s1 = "This is a great product";
        String s2 = "This is a great";
        
        double similarity = calc.similarity(s1, s2);
        assertTrue(similarity > 0.5, "Substring should have moderate-high similarity, got: " + similarity);
        assertTrue(similarity < 1.0, "Substring should not be identical");
    }

    @Test
    void transpositionsShouldReduceSimilarity() {
        // "hello" vs "hlelo" -> transposition
        double s = calc.similarity("hello", "hlelo");
        assertTrue(s > 0.5 && s < 1.0, 
                "Transposition should reduce similarity but not drastically, got: " + s);
    }

    @Test
    void multipleEditsReduceSimilarityMoreThanSingleEdit() {
        double singleEdit = calc.similarity("kitten", "sitten"); // 1 substitution
        double multipleEdits = calc.similarity("kitten", "sitting"); // 3 edits
        
        assertTrue(singleEdit > multipleEdits,
                "Single edit should have higher similarity than multiple edits");
    }

    @Test
    void similarReviewTextsHaveHighSimilarity() {
        String review1 = "Great product highly recommend";
        String review2 = "Great product I highly recommend";
        
        double s = calc.similarity(review1, review2);
        assertTrue(s > 0.8, "Similar review texts should have high similarity, got: " + s);
    }

    @Test
    void duplicateReviewsHaveVeryHighSimilarity() {
        String review1 = "Amazing product best purchase ever";
        String review2 = "Amazing product best purchase ever!";
        
        double s = calc.similarity(review1, review2);
        assertTrue(s > 0.95, "Nearly identical reviews should have very high similarity, got: " + s);
    }

    @Test
    void differentReviewsHaveLowSimilarity() {
        String review1 = "Great quality fast shipping";
        String review2 = "Terrible product broke immediately";
        
        double s = calc.similarity(review1, review2);
        assertTrue(s < 0.5, "Different reviews should have low similarity, got: " + s);
    }

    @Test
    void symmetricSimilarity() {
        String s1 = "Hello World";
        String s2 = "Hello Earth";
        
        double sim1 = calc.similarity(s1, s2);
        double sim2 = calc.similarity(s2, s1);
        
        assertEquals(sim1, sim2, 1e-9, "Similarity should be symmetric");
    }

    @Test
    void longerTextsDontBreakCalculation() {
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        
        for (int i = 0; i < 50; i++) {
            sb1.append("excellent product ");
            sb2.append("excellent product ");
        }
        sb2.append("great value");
        
        double s = calc.similarity(sb1.toString(), sb2.toString());
        assertTrue(s > 0.95, "Long similar texts should have high similarity, got: " + s);
    }

    @Test
    void performanceWithReasonablySizedText() {
        String text1 = "This is an excellent product with great quality and fast shipping. " +
                       "I highly recommend it to everyone. Best purchase ever!";
        String text2 = "This is an excellent product with great quality and fast shipping. " +
                       "I highly recommend it to everyone. Best purchase of the year!";
        
        long start = System.nanoTime();
        double s = calc.similarity(text1, text2);
        long elapsed = System.nanoTime() - start;
        
        assertTrue(s > 0.9, "Similar texts should have high similarity");
        assertTrue(elapsed < 10_000_000, // 10ms
                "Similarity should compute quickly, took: " + elapsed / 1_000_000 + "ms");
    }
}
