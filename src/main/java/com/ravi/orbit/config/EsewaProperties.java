package com.ravi.orbit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "esewa")
@Getter
@Setter
public class EsewaProperties {

    private String productCode;
    private String secretKey;
    private String paymentUrl;
    private String successCallbackUrl;
    private String failureCallbackUrl;

    private String frontendSuccessUrl;
    private String frontendFailureUrl;
    private String statusUrl;
}