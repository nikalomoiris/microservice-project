package nik.kalomiris.review_service.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
// Removed Spring context dependencies for manual mocking
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class ReviewControllerTests {

    private MockMvc mockMvc;
    private ReviewService reviewService;
    private ReviewController reviewController;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reviewService = Mockito.mock(ReviewService.class);
        reviewController = new ReviewController(reviewService);
        mockMvc = MockMvcBuilders.standaloneSetup(reviewController).build();
    }

    @Test
    void shouldCreateReview() throws Exception {
        Review review = new Review();
        review.setId(1L);
        review.setProductId(1L);
        review.setRating(5);
        review.setComment("Great product!");

        Mockito.when(reviewService.createReview(Mockito.any(Review.class))).thenReturn(review);

        mockMvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.productId").value(1L))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Great product!"));
    }

    @Test
    void shouldGetAllReviews() throws Exception {
        Review review1 = new Review();
        review1.setId(1L);
        review1.setProductId(1L);
        review1.setRating(5);
        review1.setComment("Great product!");

        Review review2 = new Review();
        review2.setId(2L);
        review2.setProductId(1L);
        review2.setRating(4);
        review2.setComment("Good product.");

        Mockito.when(reviewService.getAllReviews()).thenReturn(Arrays.asList(review1, review2));

        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L));
    }

    @Test
    void shouldGetReviewById() throws Exception {
        Review review = new Review();
        review.setId(1L);
        review.setProductId(1L);
        review.setRating(5);
        review.setComment("Great product!");

        Mockito.when(reviewService.getReviewById(1L)).thenReturn(Optional.of(review));

        mockMvc.perform(get("/api/reviews/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.productId").value(1L));
    }

    @Test
    void shouldGetReviewsByProductId() throws Exception {
        Review review1 = new Review();
        review1.setId(1L);
        review1.setProductId(1L);
        review1.setRating(5);
        review1.setComment("Great product!");
        review1.setStatus(ReviewStatus.APPROVED);

        Review review2 = new Review();
        review2.setId(2L);
        review2.setProductId(1L);
        review2.setRating(4);
        review2.setComment("Good product.");
        review2.setStatus(ReviewStatus.APPROVED);

        Mockito.when(reviewService.getReviewsByProductIdAndStatus(1L, ReviewStatus.APPROVED))
                .thenReturn(Arrays.asList(review1, review2));

        mockMvc.perform(get("/api/reviews/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].productId").value(1L))
                .andExpect(jsonPath("$[1].productId").value(1L));
    }

    @Test
    void shouldAddUpvote() throws Exception {
        Long reviewId = 1L;
        Review review = new Review();
        review.setId(reviewId);
        review.setUpvotes(0);

        Mockito.when(reviewService.addUpVote(reviewId)).thenReturn(review);

        mockMvc.perform(post("/api/reviews/{id}/upvote", reviewId))
                .andExpect(status().isOk());

        Mockito.verify(reviewService, Mockito.times(1)).addUpVote(reviewId);
    }

    @Test
    void shouldAddDownvote() throws Exception {
        Long reviewId = 1L;
        Review review = new Review();
        review.setId(reviewId);
        review.setDownvotes(0);

        Mockito.when(reviewService.addDownVote(reviewId)).thenReturn(review);

        mockMvc.perform(post("/api/reviews/{id}/downvote", reviewId))
                .andExpect(status().isOk());

        Mockito.verify(reviewService, Mockito.times(1)).addDownVote(reviewId);
    }
}
