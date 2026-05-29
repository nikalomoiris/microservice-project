package nik.kalomiris.payment_service.provider.dto;

/**
 * Result payload for provider refund attempts.
 */
public class ProviderRefundResult {

    private boolean success;
    private String refundId;
    private String errorCode;
    private String message;
    private FailureType failureType;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public void setFailureType(FailureType failureType) {
        this.failureType = failureType;
    }

    public static SuccessBuilder success() {
        return new SuccessBuilder();
    }

    public static FailureBuilder failure() {
        return new FailureBuilder();
    }

    /**
     * Builder for successful refund responses.
     */
    public static class SuccessBuilder {
        private String refundId;
        private String message;

        public SuccessBuilder withRefundId(String refundId) {
            this.refundId = refundId;
            return this;
        }

        public SuccessBuilder withMessage(String message) {
            this.message = message;
            return this;
        }

        public ProviderRefundResult build() {
            if (this.message == null) {
                throw new IllegalStateException("Message must be provided for a successful refund result");
            }
            if (this.refundId == null) {
                throw new IllegalStateException("Refund ID must be provided for a successful refund result");
            }
            ProviderRefundResult result = new ProviderRefundResult();
            result.setSuccess(true);
            result.setRefundId(refundId);
            result.setMessage(message);
            return result;
        }
    }

    /**
     * Builder for failed refund responses.
     */
    public static class FailureBuilder {
        private String errorCode;
        private String message;
        private FailureType failureType;

        public FailureBuilder withErrorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public FailureBuilder withMessage(String message) {
            this.message = message;
            return this;
        }

        public FailureBuilder withFailureType(FailureType failureType) {
            this.failureType = failureType;
            return this;
        }

        public ProviderRefundResult build() {
            if (this.message == null) {
                throw new IllegalStateException("Message must be provided for a failed refund result");
            }
            if (this.errorCode == null) {
                throw new IllegalStateException("Error code must be provided for a failed refund result");
            }
            if (this.failureType == null) {
                throw new IllegalStateException("Failure type must be provided for a failed refund result");
            }
            ProviderRefundResult result = new ProviderRefundResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setMessage(message);
            result.setFailureType(failureType);
            return result;
        }
    }

}
