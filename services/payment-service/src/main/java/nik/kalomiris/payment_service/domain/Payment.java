package nik.kalomiris.payment_service.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.validation.constraints.*;
import jakarta.persistence.*;

/**
 * Payment aggregate persisted by the payment service.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_order_id", columnList = "orderId")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Status cannot be null")
    private PaymentStatus status;

    @Positive(message = "Amount must be positive")
    @NotNull(message = "Amount cannot be null")
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Currency cannot be null")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;

    private String providerIntentId;

    private int retryCount;

    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @NotNull(message = "orderId cannot be null")
    private String orderId;

    // Constructors
    public Payment() {
    }

    private Payment(String orderId, BigDecimal amount, String currency, PaymentStatus status, String providerIntentId,
            int retryCount) {
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.providerIntentId = providerIntentId;
        this.retryCount = retryCount;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getProviderIntentId() {
        return providerIntentId;
    }

    public void setProviderIntentId(String providerIntentId) {
        this.providerIntentId = providerIntentId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Builder for easier object creation
    /**
     * Builder for constructing validated {@link Payment} instances.
     */
    public static class Builder {
        private String orderId;
        private BigDecimal amount;
        private String currency = "USD"; // Default to USD
        private PaymentStatus status = PaymentStatus.CREATED; // Default to CREATED
        private String providerIntentId;
        private int retryCount = 0; // Default to 0

        public Builder withOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder withAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder withCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder withStatus(PaymentStatus status) {
            this.status = status;
            return this;
        }

        public Builder withProviderIntentId(String providerIntentId) {
            this.providerIntentId = providerIntentId;
            return this;
        }

        public Builder withRetryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Payment build() {
            return new Payment(orderId, amount, currency, status, providerIntentId, retryCount);
        }
    }

}
