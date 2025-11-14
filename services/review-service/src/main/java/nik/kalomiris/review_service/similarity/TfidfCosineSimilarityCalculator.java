package nik.kalomiris.review_service.similarity;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * TF-IDF + Cosine similarity calculator with full IDF computation.
 *
 * This implementation computes term-frequency (TF) with log scaling,
 * inverse document frequency (IDF) with smoothing, and cosine similarity
 * between TF-IDF weighted vectors.
 *
 * Thread-safe and stateless - safe to use as a singleton Spring bean.
 *
 * Algorithm:
 * - TF: log-scaled term frequency: 1 + ln(count) if count > 0, else 0
 * - IDF: smoothed inverse document frequency: ln((N + 1) / (df + 1)) + 1
 * - TF-IDF: tf * idf for each term
 * - Similarity: cosine of TF-IDF vectors
 *
 * Performance: O(V) per document where V is vocabulary size.
 * For batch comparisons (1 new vs N existing), precompute IDF once.
 */
public class TfidfCosineSimilarityCalculator implements SimilarityCalculator {

    // Common English stop words to ignore (minimal set for better similarity)
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
            "to", "was", "will", "with"));

    public TfidfCosineSimilarityCalculator() {
        // Stateless calculator - no initialization needed
    }

    @Override
    public String name() {
        return "tfidf-cosine";
    }

    @Override
    public double similarity(String a, String b) {
        if (a == null)
            a = "";
        if (b == null)
            b = "";
        if (a.equals(b))
            return 1.0;

        // For pairwise comparison, compute IDF across both documents
        List<String> docs = Arrays.asList(a, b);
        Map<String, Double> idf = computeIdf(docs);

        Map<String, Integer> countsA = tokenizeAndCount(a);
        Map<String, Integer> countsB = tokenizeAndCount(b);

        Map<String, Double> vectorA = tfidfVector(countsA, idf);
        Map<String, Double> vectorB = tfidfVector(countsB, idf);

        return cosineSimilarity(vectorA, vectorB);
    }

    /**
     * Tokenize text and count term occurrences.
     * Applies normalization, stop-word removal, and minimum token length filter.
     *
     * @param text input text
     * @return map of term -> count
     */
    Map<String, Integer> tokenizeAndCount(String text) {
        Map<String, Integer> counts = new HashMap<>();
        if (text == null || text.isEmpty())
            return counts;

        String normalized = normalize(text);
        StringTokenizer st = new StringTokenizer(normalized);

        while (st.hasMoreTokens()) {
            String token = st.nextToken();

            // Filter: min length 2, skip stop words
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }

            counts.put(token, counts.getOrDefault(token, 0) + 1);
        }

        return counts;
    }

    /**
     * Compute IDF (inverse document frequency) for all terms in the corpus.
     * Uses smoothed IDF formula: ln((N + 1) / (df + 1)) + 1
     *
     * @param documents collection of document texts
     * @return map of term -> idf score
     */
    Map<String, Double> computeIdf(Collection<String> documents) {
        Map<String, Double> idf = new HashMap<>();
        if (documents == null || documents.isEmpty())
            return idf;

        int numDocs = documents.size();
        Map<String, Integer> documentFrequency = new HashMap<>();

        // Count in how many documents each term appears
        for (String doc : documents) {
            Map<String, Integer> counts = tokenizeAndCount(doc);
            for (String term : counts.keySet()) {
                documentFrequency.put(term, documentFrequency.getOrDefault(term, 0) + 1);
            }
        }

        // Compute IDF: ln((N + 1) / (df + 1)) + 1
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            String term = entry.getKey();
            int df = entry.getValue();
            double idfScore = Math.log((numDocs + 1.0) / (df + 1.0)) + 1.0;
            idf.put(term, idfScore);
        }

        return idf;
    }

    /**
     * Build TF-IDF vector for a document given term counts and IDF map.
     * TF uses log scaling: 1 + ln(count) for count > 0
     *
     * @param termCounts map of term -> raw count
     * @param idf        map of term -> idf score
     * @return map of term -> tfidf weight
     */
    Map<String, Double> tfidfVector(Map<String, Integer> termCounts, Map<String, Double> idf) {
        Map<String, Double> vector = new HashMap<>();

        for (Map.Entry<String, Integer> entry : termCounts.entrySet()) {
            String term = entry.getKey();
            int count = entry.getValue();

            // Log-scaled TF: 1 + ln(count)
            double tf = 1.0 + Math.log(count);

            // Get IDF (default to 1.0 if term not in IDF map)
            double idfScore = idf.getOrDefault(term, 1.0);

            vector.put(term, tf * idfScore);
        }

        return vector;
    }

    /**
     * Compute cosine similarity between two TF-IDF vectors.
     *
     * @param v1 first vector
     * @param v2 second vector
     * @return cosine similarity in [0, 1]
     */
    double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        if (v1.isEmpty() && v2.isEmpty())
            return 1.0;
        if (v1.isEmpty() || v2.isEmpty())
            return 0.0;

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        // Compute dot product and norm of v1
        for (Map.Entry<String, Double> entry : v1.entrySet()) {
            String term = entry.getKey();
            double weight1 = entry.getValue();
            norm1 += weight1 * weight1;

            Double weight2 = v2.get(term);
            if (weight2 != null) {
                dotProduct += weight1 * weight2;
            }
        }

        // Compute norm of v2
        for (double weight : v2.values()) {
            norm2 += weight * weight;
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denominator == 0.0)
            return 0.0;

        double raw = dotProduct / denominator;
        return SimilarityCalculator.clamp(raw);
    }

    /**
     * Normalize text: lowercase and replace non-alphanumeric with spaces.
     *
     * @param text input text
     * @return normalized text
     */
    private String normalize(String text) {
        if (text == null)
            return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
