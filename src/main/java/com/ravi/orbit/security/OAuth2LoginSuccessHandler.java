package com.ravi.orbit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.entity.RefreshToken;
import com.ravi.orbit.entity.Role;
import com.ravi.orbit.entity.User;
import com.ravi.orbit.entity.UserRoles;
import com.ravi.orbit.enums.EAuthProvider;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import com.ravi.orbit.exceptions.BadRequestException;
import com.ravi.orbit.repository.RefreshTokenRepository;
import com.ravi.orbit.repository.RoleRepository;
import com.ravi.orbit.repository.UserRepository;
import com.ravi.orbit.repository.UserRolesRepository;
import com.ravi.orbit.service.IUserService;
import com.ravi.orbit.utils.CommonMethods;
import com.ravi.orbit.utils.JwtUtil;
import com.ravi.orbit.utils.MyConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRolesRepository userRolesRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final IUserService userService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${oauth2.frontend.origin}")
    private String frontendOrigin;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        HttpSession session = request.getSession(false);

        String deviceId = null;
        String deviceName = null;

        if (session != null) {
            deviceId = (String) session.getAttribute(MyConstants.OAUTH_DEVICE_ID);

            deviceName = (String) session.getAttribute(MyConstants.OAUTH_DEVICE_NAME);
        }

        if (deviceId == null) {
            deviceId = "web-client";
        }

        if (deviceName == null) {
            deviceName = "Google OAuth";
        }

        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();

        String providerId = googleUser.getAttribute("sub");

        String email = googleUser.getAttribute("email");

        String firstName = googleUser.getAttribute("given_name");

        String lastName = googleUser.getAttribute("family_name");

        String imageUrl = googleUser.getAttribute("picture");

        if (providerId == null || providerId.isBlank()) {
            throw new BadRequestException("Google account ID not found");
        }

        if (email == null || email.isBlank()) {
            throw new BadRequestException("Google account email not found");
        }

        User user = findOrCreateGoogleUser(providerId, email, firstName, lastName, imageUrl);

        Role role = roleRepository.findRoleByUsername(user.getUsername())
                .orElseThrow(() -> new BadRequestException("Role not found for user"));

        String accessToken = jwtUtil.generateJwtToken(user.getUsername(), role.getTitle());

        RefreshToken refreshToken = createRefreshToken(user.getUsername(), deviceId, deviceName);

        UserDTO userDTO = userService.getUserDTOByUsername(user.getUsername());

        AuthDTO authDTO = new AuthDTO();

        authDTO.setUserDTO(userDTO);
        authDTO.setAccessToken(accessToken);
        authDTO.setRefreshToken(refreshToken.getToken());

        /*
         * OAuth2 login happened in a browser redirect.
         *
         * Send the result to the React window that
         * opened the OAuth popup.
         */
        String json = objectMapper.writeValueAsString(authDTO);

        String safeJson = json.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("</", "<\\/");

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Google Login</title>
                </head>
                <body>
                    <script>
                        (function () {
                            const authData = %s;

                            if (window.opener) {
                                window.opener.postMessage(
                                    {
                                        type: "GOOGLE_AUTH_SUCCESS",
                                        data: authData
                                    },
                                    "%s"
                                );

                                window.close();
                            } else {
                                window.location.href =
                                    "%s";
                            }
                        })();
                    </script>

                    <p>Google login successful. You can close this window.</p>
                </body>
                </html>
                """.formatted(
                safeJson,
                frontendOrigin,
                frontendOrigin
        ));

        /*
         * OAuth authorization data is no longer needed.
         */
        if (session != null) {
            session.removeAttribute(MyConstants.OAUTH_DEVICE_ID);

            session.removeAttribute(MyConstants.OAUTH_DEVICE_NAME);

            session.invalidate();
        }
    }

    private User findOrCreateGoogleUser(String providerId, String email, String firstName, String lastName, String imageUrl) {

        /*
         * 1. Find existing Google account by Google provider ID.
         */
        Optional<User> googleUser = userRepository.findByProviderAndProviderId(EAuthProvider.GOOGLE, providerId);

        if (googleUser.isPresent()) {

            User user = googleUser.get();

            /*
             * Refresh profile information from Google.
             */
            updateGoogleProfile(user, firstName, lastName, imageUrl);

            return userRepository.save(user);
        }

        /*
         * 2. Check whether this email already belongs
         *    to an existing Orbit account.
         *
         *    If it does, link the Google account to the
         *    existing user instead of throwing an exception.
         */
        Optional<User> existingByEmail = userRepository.findByEmail(email);

        if (existingByEmail.isPresent()) {

            User user = existingByEmail.get();

            /*
             * Link the existing Orbit account with Google.
             *
             * Do NOT create another User.
             */
            user.setProvider(EAuthProvider.GOOGLE);
            user.setProviderId(providerId);

            /*
             * Keep the existing password.
             *
             * This means the user can continue using:
             *
             *   Email + Password
             *
             * as well as:
             *
             *   Continue with Google
             */
            if (!CommonMethods.isEmpty(firstName)) {
                user.setFirstName(firstName);
            }

            if (!CommonMethods.isEmpty(lastName)) {
                user.setLastName(lastName);
            }

            if (!CommonMethods.isEmpty(imageUrl)) {
                user.setImageUrl(imageUrl);
            }

            return userRepository.save(user);
        }

        /*
         * 3. No existing account.
         *
         *    Create a new Google user.
         */
        User user = new User();

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);

        /*
         * Use email as the username because your normal
         * login flow already uses username/email.
         */
        user.setUsername(email);

        /*
         * Google users do not need a local password.
         */
        user.setPassword(null);

        user.setProvider(EAuthProvider.GOOGLE);
        user.setProviderId(providerId);

        user.setImageUrl(imageUrl);
        user.setStatus(EStatus.ACTIVE);

        userRepository.save(user);

        /*
         * 4. Assign normal USER role.
         */
        Role role = roleRepository.findByTitle(ERole.ROLE_USER)
                .orElseThrow(() -> new BadRequestException("ROLE_USER not found"));

        UserRoles userRoles = new UserRoles();
        userRoles.setUser(user);
        userRoles.setRole(role);

        userRolesRepository.save(userRoles);

        return user;
    }

    private void updateGoogleProfile(User user, String firstName, String lastName, String imageUrl) {

        if (firstName != null && !firstName.isBlank()) {
            user.setFirstName(firstName);
        }

        if (lastName != null && !lastName.isBlank()) {
            user.setLastName(lastName);
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            user.setImageUrl(imageUrl);
        }

        userRepository.save(user);
    }

    private String generateUniqueUsername(String email) {

        String base = email.substring(0, email.indexOf("@"));

        base = base.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

        if (base.isBlank()) {
            base = "user";
        }

        String username = base;

        int counter = 1;

        while (userRepository.existsByUsername(username)) {
            username = base + counter++;
        }

        return username;
    }

    private RefreshToken createRefreshToken(String username, String deviceId, String deviceName) {

        RefreshToken token = new RefreshToken();

        token.setToken(UUID.randomUUID().toString());

        token.setUsername(username);

        token.setDeviceId(deviceId);

        token.setDeviceName(deviceName);

        token.setExpiryDate(LocalDateTime.now().plusDays(7));

        token.setRevoked(false);

        return refreshTokenRepository.save(token);
    }
}