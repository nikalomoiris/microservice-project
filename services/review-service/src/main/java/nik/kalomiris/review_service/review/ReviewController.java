package nik.kalomiris.review_service.review;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    /**
     * REST endpoints for creating and querying reviews. Controller keeps
     * request/response mapping simple and delegates to {@link ReviewService}.
     */

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<Review> createReview(@RequestBody Review review) {
        Review createdReview = reviewService.createReview(review);
        return new ResponseEntity<>(createdReview, HttpStatus.CREATED);
    }

    @GetMapping
    public List<Review> getAllReviews() {
        return reviewService.getAllReviews();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Review> getReviewById(@PathVariable Long id) {
        return reviewService.getReviewById(id)
                .map(review -> new ResponseEntity<>(review, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/product/{productId}")
    public List<Review> getReviewsByProductId(
            @PathVariable Long productId,
            @RequestParam(required = false) ReviewStatus status) {
        if (status != null) {
            return reviewService.getReviewsByProductIdAndStatus(productId, status);
        }
        // Default: return only APPROVED reviews for backward compatibility
        return reviewService.getReviewsByProductIdAndStatus(productId, ReviewStatus.APPROVED);
    }

    @GetMapping("/status/{status}")
    public List<Review> getReviewsByStatus(@PathVariable ReviewStatus status) {
        return reviewService.getReviewsByStatus(status);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Review> updateReviewStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {
        Review updatedReview = reviewService.updateReviewStatus(
                id,
                request.getStatus(),
                request.getModeratorId());
        return new ResponseEntity<>(updatedReview, HttpStatus.OK);
    }

    @PostMapping("/{id}/upvote")
    public ResponseEntity<Review> saveUpVote(@PathVariable Long id) {
        Review updatedReview = reviewService.addUpVote(id);
        return new ResponseEntity<>(updatedReview, HttpStatus.OK);
    }

    @PostMapping("/{id}/downvote")
    public ResponseEntity<Review> saveDownVote(@PathVariable Long id) {
        Review updatedReview = reviewService.addDownVote(id);
        return new ResponseEntity<>(updatedReview, HttpStatus.OK);
    }
}
