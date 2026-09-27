package com.ravi.orbit.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@RequiredArgsConstructor
public class EnvironmentConfiguration {

    public final Environment env;

    public String getActiveProfile() {
        return env.getProperty("name");
    }

    public String getBaseURL() {
        return env.getProperty("baseurl");
    }

    public String getProductionBaseURL() {
        return "https://orbit-backend.onrender.com";
    }
}
