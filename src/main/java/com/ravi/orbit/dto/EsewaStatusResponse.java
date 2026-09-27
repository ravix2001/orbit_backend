package com.ravi.orbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Response from eSewa's ePay v2 transaction status-check API
 * (GET {statusUrl}?product_code&total_amount&transaction_uuid).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EsewaStatusResponse {

    private String productCode;

    private String transactionUuid;

    private BigDecimal totalAmount;

    // COMPLETE, PENDING, FULL_REFUND, PARTIAL_REFUND, AMBIGUOUS, NOT_FOUND, CANCELED
    private String status;

    private String refId;

}
