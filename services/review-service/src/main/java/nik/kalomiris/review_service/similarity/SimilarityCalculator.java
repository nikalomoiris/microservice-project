package nik.kalomiris.review_service.similarity;

/**
 * Interface defining a similarity calculation between two text inputs.
 *
 * Implementations must return a score in the closed range [0.0, 1.0], where
 * 0.0 means no similarity and 1.0 means identical according to the algorithm.
 *
 * Implementations should be thread-safe and deterministic for the same inputs.
 */
public interface SimilarityCalculator {

    /**
     * A short name for the algorithm (e.g. "tfidf-cosine", "levenshtein").
     */
    String name();

    /**
     * Calculate similarity between two texts. Implementations must ensure the
     * returned value is within [0.0, 1.0].
     *
     * Notes:
     * - Implementations may normalize or preprocess inputs as needed.
     * - Null inputs should be treated as empty strings (implementations may
     * also choose to throw IllegalArgumentException, but treating null as "" is
     * recommended for robustness).
     *
     * @param a first text
     * @param b second text
     * @return similarity score between 0.0 and 1.0 inclusive
     */
    double similarity(String a, String b);

    /**
     * Convenience default that normalizes nulls to empty strings and clamps the
     * result to the [0.0, 1.0] range. Implementations can call this helper from
     * their {@link #similarity(String, String)} implementations to get consistent
     * null-handling and bounding behaviour.
     *
     * @param rawResult raw similarity value (may be outside [0,1])
     * @return clipped value inside [0.0, 1.0]
     */
    static double clamp(double rawResult) {
        if (Double.isNaN(rawResult))
            return 0.0;
        if (rawResult < 0.0)
            return 0.0;
        if (rawResult > 1.0)
            return 1.0;
        return rawResult;
    }
}
