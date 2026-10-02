package com.ravi.orbit.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler
        implements AuthenticationFailureHandler {

    @Value("${oauth2.frontend.origin}")
    private String frontendOrigin;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        String message =
                exception.getMessage() != null
                        ? exception.getMessage()
                        : "Google authentication failed";

        String safeMessage =
                message
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", " ")
                        .replace("\r", " ");

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write("""
                <!DOCTYPE html>
                <html>
                <body>
                    <script>
                        if (window.opener) {
                            window.opener.postMessage(
                                {
                                    type: "GOOGLE_AUTH_ERROR",
                                    message: "%s"
                                },
                                "%s"
                            );

                            window.close();
                        } else {
                            window.location.href = "%s";
                        }
                    </script>

                    <p>Google login failed.</p>
                </body>
                </html>
                """.formatted(
                safeMessage,
                frontendOrigin,
                frontendOrigin
        ));
    }
}