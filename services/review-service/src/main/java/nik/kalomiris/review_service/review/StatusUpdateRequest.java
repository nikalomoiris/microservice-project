package nik.kalomiris.review_service.review;

/**
 * Request DTO for updating review status via moderation endpoint.
 */
public class StatusUpdateRequest {
    private ReviewStatus status;
    private String moderatorId;

    /**
     * Default constructor required for JSON deserialization.
     */
    public StatusUpdateRequest() {
        // Required by Jackson for JSON deserialization
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public String getModeratorId() {
        return moderatorId;
    }

    public void setModeratorId(String moderatorId) {
        this.moderatorId = moderatorId;
    }
}
