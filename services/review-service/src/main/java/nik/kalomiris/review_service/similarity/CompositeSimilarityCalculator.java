package nik.kalomiris.review_service.similarity;

import java.util.Objects;

/**
 * Composite similarity calculator that combines two underlying calculators
 * using configurable weights.
 *
 * Example: composite = w1 * s1 + w2 * s2
 */
public class CompositeSimilarityCalculator implements SimilarityCalculator {

    private final SimilarityCalculator primary;
    private final SimilarityCalculator secondary;
    private final double primaryWeight;
    private final double secondaryWeight;

    /**
     * Construct a composite calculator.
     * Weights should sum to (approximately) 1.0; the constructor will normalize
     * them if they do not.
     */
    public CompositeSimilarityCalculator(SimilarityCalculator primary,
            SimilarityCalculator secondary,
            double primaryWeight,
            double secondaryWeight) {
        this.primary = Objects.requireNonNull(primary, "primary calculator");
        this.secondary = Objects.requireNonNull(secondary, "secondary calculator");
        double sum = primaryWeight + secondaryWeight;
        if (sum <= 0.0) {
            // fallback to equal weights
            this.primaryWeight = 0.5;
            this.secondaryWeight = 0.5;
        } else {
            this.primaryWeight = primaryWeight / sum;
            this.secondaryWeight = secondaryWeight / sum;
        }
    }

    @Override
    public String name() {
        return "composite(" + primary.name() + "+" + secondary.name() + ")";
    }

    @Override
    public double similarity(String a, String b) {
        double s1 = primary.similarity(a, b);
        double s2 = secondary.similarity(a, b);
        double raw = (primaryWeight * s1) + (secondaryWeight * s2);
        return SimilarityCalculator.clamp(raw);
    }
}
