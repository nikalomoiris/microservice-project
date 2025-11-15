package nik.kalomiris.review_service.similarity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TfidfCosineSimilarityCalculator}.
 * Validates TF-IDF computation, IDF calculation, and cosine similarity.
 */
class TfidfCosineSimilarityCalculatorTests {

    private final TfidfCosineSimilarityCalculator calc = new TfidfCosineSimilarityCalculator();

    @Test
    void identicalStringsShouldReturnOne() {
        double s = calc.similarity("Hello world", "Hello world");
        assertEquals(1.0, s, 1e-9, "Identical texts must have similarity 1.0");
    }

    @Test
    void nullsAreHandledAsEmptyStrings() {
        double s = calc.similarity(null, null);
        assertEquals(1.0, s, 1e-9, "Two nulls should be treated as identical empty strings");
    }

    @Test
    void emptyVsNonEmptyReturnsZero() {
        double s = calc.similarity("", "some text");
        assertEquals(0.0, s, 1e-9, "Empty vs non-empty should return 0.0");
    }

    @Test
    void completelyDifferentTextsHaveLowSimilarity() {
        String a = "The quick brown fox jumps over the lazy dog";
        String b = "Python programming language machine learning";
        double s = calc.similarity(a, b);
        assertTrue(s < 0.3, "Completely different texts should have low similarity, got: " + s);
    }

    @Test
    void nearDuplicatesHaveHighSimilarity() {
        String a = "This product is excellent and I highly recommend it";
        String b = "This product is excellent and I highly recommend it to everyone";
        double s = calc.similarity(a, b);
        assertTrue(s > 0.8, "Near-duplicate texts should have high similarity, got: " + s);
    }

    @Test
    void stopWordsAreFilteredOut() {
        // Texts differ only in stop words
        String a = "excellent product";
        String b = "the excellent product is a great product";
        double s = calc.similarity(a, b);
        // After stop-word removal, both should be very similar
        assertTrue(s > 0.6, "Stop words should be filtered, got similarity: " + s);
    }

    @Test
    void tokenizeAndCountBasic() {
        Map<String, Integer> counts = calc.tokenizeAndCount("hello world hello");
        assertEquals(2, counts.get("hello"), "hello should appear twice");
        assertEquals(1, counts.get("world"), "world should appear once");
    }

    @Test
    void tokenizeAndCountIgnoresStopWords() {
        Map<String, Integer> counts = calc.tokenizeAndCount("the quick brown fox");
        assertEquals(null, counts.get("the"), "Stop word 'the' should be filtered");
        assertEquals(1, counts.get("quick"));
        assertEquals(1, counts.get("brown"));
    }

    @Test
    void idfComputationForSimpleCorpus() {
        var docs = Arrays.asList("hello world", "hello there", "goodbye world");
        Map<String, Double> idf = calc.computeIdf(docs);

        // 'hello' appears in 2/3 docs, 'world' in 2/3, 'there' in 1/3, 'goodbye' in 1/3
        // IDF = ln((N+1)/(df+1)) + 1
        // hello: ln(4/3) + 1 ≈ 1.288
        // world: ln(4/3) + 1 ≈ 1.288
        // there: ln(4/2) + 1 ≈ 1.693
        // goodbye: ln(4/2) + 1 ≈ 1.693

        assertTrue(idf.get("hello") < idf.get("there"),
                "Common term 'hello' should have lower IDF than rare term 'there'");
        assertTrue(idf.get("world") < idf.get("goodbye"),
                "Common term 'world' should have lower IDF than rare term 'goodbye'");
    }

    @Test
    void tfidfVectorComputesCorrectly() {
        Map<String, Integer> counts = Map.of("hello", 3, "world", 1);
        Map<String, Double> idf = Map.of("hello", 1.5, "world", 2.0);

        Map<String, Double> vector = calc.tfidfVector(counts, idf);

        // TF(hello) = 1 + ln(3) ≈ 2.0986
        // TF-IDF(hello) = 2.0986 * 1.5 ≈ 3.148
        assertTrue(vector.get("hello") > 3.0 && vector.get("hello") < 3.2,
                "TF-IDF for 'hello' should be ~3.148, got: " + vector.get("hello"));

        // TF(world) = 1 + ln(1) = 1.0
        // TF-IDF(world) = 1.0 * 2.0 = 2.0
        assertEquals(2.0, vector.get("world"), 0.01,
                "TF-IDF for 'world' should be 2.0");
    }

    @Test
    void cosineSimilarityOfIdenticalVectorsIsOne() {
        Map<String, Double> v = Map.of("hello", 1.0, "world", 2.0);
        double sim = calc.cosineSimilarity(v, v);
        assertEquals(1.0, sim, 1e-9, "Cosine of identical vectors should be 1.0");
    }

    @Test
    void cosineSimilarityOfOrthogonalVectorsIsZero() {
        Map<String, Double> v1 = Map.of("hello", 1.0);
        Map<String, Double> v2 = Map.of("world", 1.0);
        double sim = calc.cosineSimilarity(v1, v2);
        assertEquals(0.0, sim, 1e-9, "Cosine of orthogonal vectors should be 0.0");
    }

    @Test
    void similarityHandlesSpecialCharacters() {
        String a = "Great product!!! 5/5 stars!!!";
        String b = "Great product 5 5 stars";
        double s = calc.similarity(a, b);
        // After normalization, both should be similar (punctuation stripped)
        assertTrue(s > 0.7, "Special characters should be normalized, got: " + s);
    }

    @Test
    void performanceWithLargeText() {
        // Generate a reasonably large review (simulate real review text)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("This is an excellent product with great quality and fast shipping. ");
        }
        String review = sb.toString();

        long start = System.nanoTime();
        double s = calc.similarity(review, review);
        long elapsed = System.nanoTime() - start;

        assertEquals(1.0, s, 1e-9, "Large identical texts should have similarity 1.0");
        assertTrue(elapsed < 50_000_000, // 50ms
                "Similarity should compute in < 50ms for ~200 words, took: " + elapsed / 1_000_000 + "ms");
    }
}
