package nik.kalomiris.review_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import nik.kalomiris.review_service.review.Review;
import nik.kalomiris.review_service.review.ReviewRepository;
import nik.kalomiris.review_service.review.ReviewStatus;
import nik.kalomiris.review_service.review.StatusUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import nik.kalomiris.review_service.config.TestMessagingConfig;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Review Similarity Evaluation System.
 * Tests end-to-end flows with H2 in-memory database.
 * 
 * Coverage:
 * - Unique review submission → APPROVED
 * - Duplicate detection → REJECTED
 * - Similarity thresholds → FOR_MODERATION
 * - Admin moderation endpoint
 * - Status filtering
 * - Backward compatibility
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMessagingConfig.class)
class ReviewIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        reviewRepository.deleteAll();
    }

    /**
     * Test 1: Unique review should be automatically APPROVED
     */
    @Test
    void submitUniqueReview_shouldBeApproved() throws Exception {
        Review review = createReview(1L, 5, "This is an excellent product with great quality!");

        MvcResult result = mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isCreated())
                .andReturn();

        Review created = objectMapper.readValue(result.getResponse().getContentAsString(), Review.class);
        assertEquals(ReviewStatus.APPROVED, created.getStatus());
        assertEquals(0.0, created.getSimilarityScore(), 0.01);
        assertNull(created.getMostSimilarReviewId());
    }

    /**
     * Test 2: Duplicate review should be REJECTED
     */
    @Test
    void submitDuplicateReview_shouldBeRejected() throws Exception {
        Review existing = createReview(1L, 5, "Amazing product, best purchase ever!");
        reviewRepository.save(existing);

        Review duplicate = createReview(1L, 5, "Amazing product, best purchase ever!");
        
        MvcResult result = mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isCreated())
                .andReturn();

        Review created = objectMapper.readValue(result.getResponse().getContentAsString(), Review.class);
        assertEquals(ReviewStatus.REJECTED, created.getStatus());
        assertTrue(created.getSimilarityScore() >= 0.85);
    }

    /**
     * Test 3: Moderately similar review should be FOR_MODERATION
     */
    @Test
    void submitModeratelySimilarReview_shouldBeForModeration() throws Exception {
        Review existing = createReview(1L, 5, "Great product, highly recommend it to everyone!");
        reviewRepository.save(existing);

        Review similar = createReview(1L, 5, "Great product, I highly recommend this item!");
        
        MvcResult result = mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(similar)))
                .andExpect(status().isCreated())
                .andReturn();

        Review created = objectMapper.readValue(result.getResponse().getContentAsString(), Review.class);
        assertEquals(ReviewStatus.FOR_MODERATION, created.getStatus());
        assertTrue(created.getSimilarityScore() >= 0.60 && created.getSimilarityScore() < 0.85);
    }

    /**
     * Test 4: Admin can moderate reviews via PATCH endpoint
     */
    @Test
    void moderateReview_shouldUpdateStatus() throws Exception {
        Review review = createReview(1L, 4, "Good product");
        review.setStatus(ReviewStatus.FOR_MODERATION);
        Review saved = reviewRepository.save(review);

        StatusUpdateRequest updateRequest = new StatusUpdateRequest();
        updateRequest.setStatus(ReviewStatus.APPROVED);
        updateRequest.setModeratorId("admin@example.com");

        mockMvc.perform(patch("/api/reviews/{id}/status", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.moderatedBy").value("admin@example.com"));
    }

    /**
     * Test 5: GET endpoint with status filtering
     */
    @Test
    void getReviewsByProductIdAndStatus_shouldFilter() throws Exception {
        Review approved = createReview(1L, 5, "Approved review");
        approved.setStatus(ReviewStatus.APPROVED);
        
        Review moderation = createReview(1L, 4, "Moderation review");
        moderation.setStatus(ReviewStatus.FOR_MODERATION);

        reviewRepository.save(approved);
        reviewRepository.save(moderation);

        mockMvc.perform(get("/api/reviews/product/1")
                .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    /**
     * Test 6: Different products should not be compared
     */
    @Test
    void reviewsFromDifferentProducts_shouldNotInterfere() throws Exception {
        Review productA = createReview(1L, 5, "Amazing product, best ever!");
        reviewRepository.save(productA);

        Review productB = createReview(2L, 5, "Amazing product, best ever!");
        
        MvcResult result = mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productB)))
                .andExpect(status().isCreated())
                .andReturn();

        Review created = objectMapper.readValue(result.getResponse().getContentAsString(), Review.class);
        assertEquals(ReviewStatus.APPROVED, created.getStatus());
    }

    private Review createReview(Long productId, int rating, String comment) {
        Review review = new Review();
        review.setProductId(productId);
        review.setRating(rating);
        review.setComment(comment);
        review.setUpvotes(0);
        review.setDownvotes(0);
        return review;
    }
}
