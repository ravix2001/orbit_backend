package com.ravi.orbit.security;

import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.service.IAuthService;
import com.ravi.orbit.utils.MyConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles a successful Google OAuth login.
 *
 * <p>The frontend opens this flow in a popup. Whatever happens, this handler ends
 * by posting a message back to the opener window (success payload or error) via
 * {@link OAuth2PopupResponseWriter} — it never lets an exception bubble up into a
 * raw servlet error page, which would leave the popup (and the SPA) hanging.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final IAuthService authService;
    private final OAuth2PopupResponseWriter responseWriter;

    @Value("${oauth2.customer.origin}")
    private String customerOrigin;

    @Value("${oauth2.seller.origin}")
    private String sellerOrigin;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        HttpSession session = request.getSession(false);

        // Resolve the requested role first so even an error goes to the right origin.
        ERole requestedRole = resolveRole(session);
        String targetOrigin = originFor(requestedRole);

        try {
            if (session == null) {
                throw new IllegalStateException("OAuth session not found");
            }
            if (requestedRole == null) {
                throw new IllegalStateException("OAuth login role not found");
            }

            String deviceId = (String) session.getAttribute(MyConstants.OAUTH_DEVICE_ID);
            String deviceName = (String) session.getAttribute(MyConstants.OAUTH_DEVICE_NAME);

            OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();

            AuthDTO authDTO = authService.processOAuth2Login(googleUser, requestedRole, deviceId, deviceName);

            responseWriter.writeSuccess(response, authDTO, targetOrigin);

        } catch (Exception ex) {
            log.error("Google OAuth success handling failed", ex);
            responseWriter.writeError(response, friendlyMessage(ex), targetOrigin);
        } finally {
            if (session != null) {
                session.invalidate();
            }
        }
    }

    private ERole resolveRole(HttpSession session) {
        if (session == null) {
            return null;
        }
        String value = (String) session.getAttribute(MyConstants.OAUTH_LOGIN_ROLE);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ERole.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String originFor(ERole role) {
        return role == ERole.ROLE_SELLER ? sellerOrigin : customerOrigin;
    }

    /**
     * Surface validation messages (which carry a safe, user-facing reason) but hide
     * unexpected internal errors behind a generic message.
     */
    private String friendlyMessage(Exception ex) {
        if (ex instanceof com.ravi.orbit.exceptions.BadRequestException
                || ex instanceof IllegalStateException) {
            return ex.getMessage();
        }
        return "Google authentication failed. Please try again.";
    }
}