package com.ravi.orbit.service;

import com.ravi.orbit.dto.EsewaPaymentInitiateResponse;
import com.ravi.orbit.dto.EsewaPaymentRequest;

public interface IEsewaPaymentService {

    EsewaPaymentInitiateResponse initiatePayment(EsewaPaymentRequest request);

    void handleSuccess(String encodedData);

    void handleFailure(String encodedData);
}