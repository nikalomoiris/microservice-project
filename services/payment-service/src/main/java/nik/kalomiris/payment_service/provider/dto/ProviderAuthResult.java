package nik.kalomiris.payment_service.provider.dto;

public class ProviderAuthResult {

    private boolean success;
    private String intentId;
    private String errorCode;
    private String message;
    private FailureType failureType;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getIntentId() {
        return intentId;
    }

    public void setIntentId(String intentId) {
        this.intentId = intentId;
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

    public static class SuccessBuilder {
        private String intentId;
        private String message;

        public SuccessBuilder withIntentId(String intentId) {
            this.intentId = intentId;
            return this;
        }

        public SuccessBuilder withMessage(String message) {
            this.message = message;
            return this;
        }

        public ProviderAuthResult build() {
            if (this.message == null) {
                throw new IllegalStateException("Message must be provided for a successful authorization result");
            }
            if (this.intentId == null) {
                throw new IllegalStateException("Intent ID must be provided for a successful authorization result");
            }
            ProviderAuthResult result = new ProviderAuthResult();
            result.setSuccess(true);
            result.setIntentId(intentId);
            result.setMessage(message);
            return result;
        }
    }

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

        public ProviderAuthResult build() {
            if (this.message == null) {
                throw new IllegalStateException("Message must be provided for a failed authorization result");
            }
            if (this.errorCode == null) {
                throw new IllegalStateException("Error code must be provided for a failed authorization result");
            }
            if (this.failureType == null) {
                throw new IllegalStateException("Failure type must be provided for a failed authorization result");
            }
            ProviderAuthResult result = new ProviderAuthResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setMessage(message);
            result.setFailureType(failureType);
            return result;
        }
    }

}
