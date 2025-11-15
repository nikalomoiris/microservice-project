package nik.kalomiris.review_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify ReviewEvaluationConfig loads correctly from
 * application.properties.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMessagingConfig.class)
class ReviewEvaluationConfigTest {

    @Autowired
    private ReviewEvaluationConfig config;

    @Test
    void testConfigurationLoaded() {
        assertNotNull(config, "ReviewEvaluationConfig should be loaded");
    }

    @Test
    void testFeatureFlagEnabled() {
        assertTrue(config.isEnabled(), "Evaluation should be enabled by default");
    }

    @Test
    void testThresholdConfiguration() {
        assertNotNull(config.getThreshold(), "Threshold configuration should not be null");
        assertEquals(0.60, config.getThreshold().getApproved(), 0.001,
                "Approved threshold should be 0.60");
        assertEquals(0.85, config.getThreshold().getModeration(), 0.001,
                "Moderation threshold should be 0.85");
        assertTrue(config.getThreshold().isValid(),
                "Thresholds should be valid (approved < moderation)");
    }

    @Test
    void testWeightConfiguration() {
        assertNotNull(config.getWeight(), "Weight configuration should not be null");
        assertEquals(0.7, config.getWeight().getCosine(), 0.001,
                "Cosine weight should be 0.7");
        assertEquals(0.3, config.getWeight().getLevenshtein(), 0.001,
                "Levenshtein weight should be 0.3");
        assertTrue(config.getWeight().isValid(),
                "Weights should sum to 1.0");
    }

    @Test
    void testPerformanceConfiguration() {
        assertEquals(1000, config.getMaxComparisons(),
                "Max comparisons should be 1000");
    }

    @Test
    void testCacheConfiguration() {
        assertNotNull(config.getCache(), "Cache configuration should not be null");
        assertTrue(config.getCache().isEnabled(), "Cache should be enabled by default");
        assertEquals(3600, config.getCache().getTtl(),
                "Cache TTL should be 3600 seconds");
    }

    @Test
    void testOverallConfigurationValidity() {
        assertTrue(config.isValid(),
                "Overall configuration should be valid");
    }

    @Test
    void testToString() {
        String configString = config.toString();
        assertNotNull(configString, "toString should not return null");
        assertTrue(configString.contains("enabled=true"),
                "toString should contain enabled flag");
        assertTrue(configString.contains("threshold.approved=0.6"),
                "toString should contain approved threshold");
    }
}
