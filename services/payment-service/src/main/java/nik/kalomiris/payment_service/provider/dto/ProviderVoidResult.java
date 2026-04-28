package nik.kalomiris.payment_service.provider.dto;

public class ProviderVoidResult {

    private boolean success;
    private String errorCode;
    private String message;
    private FailureType failureType;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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
        private String message;

        public SuccessBuilder withMessage(String message) {
            this.message = message;
            return this;
        }

        public ProviderVoidResult build() {
            if (this.message == null) {
                throw new IllegalStateException("Message must be provided for a successful void result");
            }
            ProviderVoidResult result = new ProviderVoidResult();
            result.setSuccess(true);
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

        public ProviderVoidResult build() {
            if (this.message == null) {
                throw new IllegalStateException("Message must be provided for a failed void result");
            }
            if (this.errorCode == null) {
                throw new IllegalStateException("Error code must be provided for a failed void result");
            }
            if (this.failureType == null) {
                throw new IllegalStateException("Failure type must be provided for a failed void result");
            }
            ProviderVoidResult result = new ProviderVoidResult();
            result.setSuccess(false);
            result.setErrorCode(errorCode);
            result.setMessage(message);
            result.setFailureType(failureType);
            return result;
        }
    }

}
