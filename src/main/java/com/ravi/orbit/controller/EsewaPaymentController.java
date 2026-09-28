package com.ravi.orbit.controller;

import com.ravi.orbit.config.EnvironmentConfiguration;
import com.ravi.orbit.config.EsewaProperties;
import com.ravi.orbit.dto.EsewaPaymentInitiateResponse;
import com.ravi.orbit.dto.EsewaPaymentRequest;
import com.ravi.orbit.service.IEsewaPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/payment/esewa")
@RequiredArgsConstructor
public class EsewaPaymentController {

    private final IEsewaPaymentService esewaPaymentService;

    private final EsewaProperties esewaProperties;

    @PostMapping("/initiate")
    public ResponseEntity<EsewaPaymentInitiateResponse> initiatePayment(@RequestBody EsewaPaymentRequest request) {
        return ResponseEntity.ok(esewaPaymentService.initiatePayment(request));
    }

    @GetMapping("/success")
    public ResponseEntity<Void> success(@RequestParam String data) {
        esewaPaymentService.handleSuccess(data);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(
                        URI.create(
                                esewaProperties.getFrontendSuccessUrl()
                        )
                )
                .build();
    }

    @GetMapping("/failure")
    public ResponseEntity<Void> failure(@RequestParam(required = false) String data) {
        esewaPaymentService.handleFailure(data);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(
                        URI.create(
                                esewaProperties.getFrontendFailureUrl()
                        )
                )
                .build();
    }
}
