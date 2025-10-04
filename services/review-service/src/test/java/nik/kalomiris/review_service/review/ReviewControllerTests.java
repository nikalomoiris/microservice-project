package nik.kalomiris.review_service.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(ReviewController.class)
public class ReviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateReview() throws Exception {
        Review review = new Review();
        review.setId(1L);
        review.setProductId(1L);
        review.setRating(5);
        review.setComment("Great product!");

        when(reviewService.createReview(any(Review.class))).thenReturn(review);

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

        when(reviewService.getAllReviews()).thenReturn(Arrays.asList(review1, review2));

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

        when(reviewService.getReviewById(1L)).thenReturn(Optional.of(review));

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

        Review review2 = new Review();
        review2.setId(2L);
        review2.setProductId(1L);
        review2.setRating(4);
        review2.setComment("Good product.");

        when(reviewService.getReviewsByProductId(1L)).thenReturn(Arrays.asList(review1, review2));

        mockMvc.perform(get("/api/reviews/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].productId").value(1L))
                .andExpect(jsonPath("$[1].productId").value(1L));
    }
}
