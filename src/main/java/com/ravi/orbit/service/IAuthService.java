package com.ravi.orbit.service;

import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.enums.ERole;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface IAuthService {

    AuthDTO userSignup(UserDTO userDTO);

    AuthDTO sellerSignup(UserDTO userDTO);

    AuthDTO userLogin(AuthDTO authDTO);

    AuthDTO sellerLogin(AuthDTO authDTO);

    AuthDTO adminLogin(AuthDTO authDTO);

    AuthDTO refreshToken(String authHeader);

    String logout(String authHeader);

    /**
     * Find-or-create the Orbit user behind a verified Google identity, ensure the
     * requested role is granted, and issue an access + refresh token pair.
     *
     * <p>Runs in a single transaction. Any failure throws (so the OAuth success
     * handler can render a clean error back to the popup) and rolls back.
     */
    AuthDTO processOAuth2Login(OAuth2User googleUser,
                               ERole requestedRole,
                               String deviceId,
                               String deviceName);

}
