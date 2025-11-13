package nik.kalomiris.review_service.similarity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Lightweight TF-IDF + Cosine similarity calculator skeleton.
 *
 * This implementation provides a small, well-documented starting point
 * that returns a deterministic cosine-like score between two texts.
 * Replace or extend the helper methods with a production-ready TF-IDF
 * implementation when you implement Phase 2.
 */
public class TfidfCosineSimilarityCalculator implements SimilarityCalculator {

    public TfidfCosineSimilarityCalculator() {
        // Placeholder constructor. In production this could accept caching
        // strategies or tokenization settings.
    }

    @Override
    public String name() {
        return "tfidf-cosine";
    }

    @Override
    public double similarity(String a, String b) {
        // Normalize nulls to empty strings per interface guidance
        if (a == null)
            a = "";
        if (b == null)
            b = "";

        // Quick path for identical strings
        if (a.equals(b)) {
            return 1.0;
        }

        // Simple TF-based vectorization as a skeleton. This is not a
        // production TF-IDF implementation but is deterministic and
        // useful for unit tests and initial wiring.
        Map<String, Double> v1 = termFrequencyVector(a);
        Map<String, Double> v2 = termFrequencyVector(b);

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (Map.Entry<String, Double> e : v1.entrySet()) {
            double w1 = e.getValue();
            norm1 += w1 * w1;
            Double w2 = v2.get(e.getKey());
            if (w2 != null) {
                dot += w1 * w2;
            }
        }
        for (double w : v2.values()) {
            norm2 += w * w;
        }

        double denom = Math.sqrt(norm1) * Math.sqrt(norm2);
        double raw = (denom == 0.0) ? 0.0 : dot / denom;

        return SimilarityCalculator.clamp(raw);
    }

    private Map<String, Double> termFrequencyVector(String text) {
        Map<String, Double> freq = new HashMap<>();
        String normalized = normalize(text);
        StringTokenizer st = new StringTokenizer(normalized);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            // very small filter to ignore 1-char noise tokens
            if (tok.length() < 2)
                continue;
            freq.put(tok, freq.getOrDefault(tok, 0.0) + 1.0);
        }
        return freq;
    }

    private String normalize(String s) {
        if (s == null)
            return "";
        // lowercase and replace non-letter/digit with space
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
