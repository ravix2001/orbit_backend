package com.ravi.orbit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the HTML response that a Google OAuth popup renders. The popup posts the
 * result back to the window that opened it (the SPA) via {@code postMessage} and
 * then closes itself. If the page was opened as a full navigation instead of a
 * popup, it falls back to redirecting to the frontend origin.
 *
 * <p>Both the success and failure handlers go through here so that the auth flow
 * ALWAYS ends with a structured message to the opener — never a raw error page.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2PopupResponseWriter {

    public static final String MSG_TYPE_SUCCESS = "GOOGLE_AUTH_SUCCESS";
    public static final String MSG_TYPE_ERROR = "GOOGLE_AUTH_ERROR";

    private final ObjectMapper objectMapper;

    /**
     * Send a successful auth payload (an AuthDTO already serialized to JSON) to the opener.
     */
    public void writeSuccess(HttpServletResponse response, Object payload, String targetOrigin)
            throws IOException {

        String payloadJson = objectMapper.writeValueAsString(payload);
        writeMessage(response, MSG_TYPE_SUCCESS, "data", payloadJson, targetOrigin);
    }

    /**
     * Send an error message to the opener.
     */
    public void writeError(HttpServletResponse response, String message, String targetOrigin)
            throws IOException {

        String messageJson = objectMapper.writeValueAsString(message);
        writeMessage(response, MSG_TYPE_ERROR, "message", messageJson, targetOrigin);
    }

    /**
     * @param payloadKey    the property name under which the payload is attached ("data"/"message")
     * @param payloadJson   already-JSON-encoded payload value
     */
    private void writeMessage(HttpServletResponse response,
                              String messageType,
                              String payloadKey,
                              String payloadJson,
                              String targetOrigin) throws IOException {

        String safePayload = escapeForScript(payloadJson);
        String originJson = escapeForScript(objectMapper.writeValueAsString(targetOrigin));

        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Google Authentication</title>
                </head>
                <body>
                    <script>
                        (function () {
                            var payload = %s;
                            var targetOrigin = %s;

                            if (window.opener) {
                                window.opener.postMessage(
                                    { type: "%s", %s: payload },
                                    targetOrigin
                                );
                                window.close();
                            } else {
                                window.location.href = targetOrigin;
                            }
                        })();
                    </script>
                    <p>You can close this window.</p>
                </body>
                </html>
                """.formatted(safePayload, originJson, messageType, payloadKey));
    }

    /**
     * Prevent a JSON value from breaking out of the surrounding &lt;script&gt; element.
     * Jackson has already produced valid JSON escaping; we only neutralise the
     * characters that are meaningful to the HTML parser.
     */
    private String escapeForScript(String json) {
        return json
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }
}
