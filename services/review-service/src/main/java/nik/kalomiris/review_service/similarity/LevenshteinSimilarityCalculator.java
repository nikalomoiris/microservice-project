package nik.kalomiris.review_service.similarity;

/**
 * Levenshtein-based similarity calculator skeleton.
 *
 * Implements a normalized similarity based on the Levenshtein edit distance:
 * similarity = 1 - (distance / maxLen) where maxLen is the length of the
 * longer string. Returns 1.0 for two empty strings.
 */
public class LevenshteinSimilarityCalculator implements SimilarityCalculator {

    public LevenshteinSimilarityCalculator() {
        // Stateless calculator - constructor intentionally empty to allow
        // simple DI and unit testing.
    }

    @Override
    public String name() {
        return "levenshtein";
    }

    @Override
    public double similarity(String a, String b) {
        if (a == null)
            a = "";
        if (b == null)
            b = "";

        if (a.equals(b))
            return 1.0;

        int dist = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        double raw = (maxLen == 0) ? 1.0 : 1.0 - ((double) dist / (double) maxLen);
        return SimilarityCalculator.clamp(raw);
    }

    // Classic iterative DP Levenshtein distance. Suitable for small strings.
    private int levenshteinDistance(String s, String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0)
            return m;
        if (m == 0)
            return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++)
            prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char si = s.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (si == t.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
