package com.ravi.orbit.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EsewaPaymentInitiateResponse {

    private String paymentUrl;

    private String amount;

    private String taxAmount;

    private String totalAmount;

    private String transactionUuid;

    private String productCode;

    private String productServiceCharge;

    private String productDeliveryCharge;

    private String successUrl;

    private String failureUrl;

    private String signedFieldNames;

    private String signature;

}