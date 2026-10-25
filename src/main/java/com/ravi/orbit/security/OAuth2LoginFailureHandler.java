package com.ravi.orbit.security;

import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.utils.MyConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles a failed Google OAuth login (user denied consent, provider error, etc.)
 * by posting a {@code GOOGLE_AUTH_ERROR} message back to the opener window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2PopupResponseWriter responseWriter;

    @Value("${oauth2.customer.origin}")
    private String customerOrigin;

    @Value("${oauth2.seller.origin}")
    private String sellerOrigin;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        log.error("Google OAuth authentication failed", exception);

        HttpSession session = request.getSession(false);
        String targetOrigin = getTargetOrigin(session);

        try {
            responseWriter.writeError(response,
                    "Google authentication failed. Please try again.", targetOrigin);
        } finally {
            if (session != null) {
                session.invalidate();
            }
        }
    }

    private String getTargetOrigin(HttpSession session) {
        if (session == null) {
            return customerOrigin;
        }
        String roleName = (String) session.getAttribute(MyConstants.OAUTH_LOGIN_ROLE);
        if (ERole.ROLE_SELLER.name().equals(roleName)) {
            return sellerOrigin;
        }
        // Safe fallback: customer origin for ROLE_USER or a missing/invalid role.
        return customerOrigin;
    }
}