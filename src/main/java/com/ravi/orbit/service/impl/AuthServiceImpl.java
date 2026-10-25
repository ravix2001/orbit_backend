package com.ravi.orbit.service.impl;

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
import com.ravi.orbit.exceptions.InvalidTokenException;
import com.ravi.orbit.repository.RefreshTokenRepository;
import com.ravi.orbit.repository.RoleRepository;
import com.ravi.orbit.repository.UserRepository;
import com.ravi.orbit.repository.UserRolesRepository;
import com.ravi.orbit.service.IAuthService;
import com.ravi.orbit.service.IUserService;
import com.ravi.orbit.utils.CommonMethods;
import com.ravi.orbit.utils.JwtUtil;
import com.ravi.orbit.utils.MyConstants;
import com.ravi.orbit.utils.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements IAuthService {

    private final IUserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRolesRepository userRolesRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public AuthDTO userSignup(UserDTO userDTO) {
        Validator.validateUserSignup(userDTO);
        return signup(userDTO, ERole.ROLE_USER);
    }

    @Override
    public AuthDTO sellerSignup(UserDTO userDTO) {
        Validator.validateSellerSignup(userDTO);
        return signup(userDTO, ERole.ROLE_SELLER);
    }

    @Override
    public AuthDTO userLogin(AuthDTO authDTO) {
        return login(authDTO, ERole.ROLE_USER);
    }

    @Override
    public AuthDTO sellerLogin(AuthDTO authDTO) {
        return login(authDTO, ERole.ROLE_SELLER);
    }

    @Override
    public AuthDTO adminLogin(AuthDTO authDTO) {
        return login(authDTO, ERole.ROLE_ADMIN);
    }

    public AuthDTO signup(UserDTO userDTO, ERole role) {

        userDTO.setEmail(normalizeEmail(userDTO.getEmail()));

        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new BadRequestException("User already exists with email: " + userDTO.getEmail());
        }

        if (!CommonMethods.isEmpty(userDTO.getPhone())
                && userRepository.existsByPhone(userDTO.getPhone())) {
            throw new BadRequestException("User already exists with phone: " + userDTO.getPhone());
        }

        User user = new User();
        userService.mapToUserEntity(user, userDTO);
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setProvider(EAuthProvider.LOCAL);
        user.setStatus(EStatus.ACTIVE);

        userRepository.save(user);

        Role roleDB = roleRepository.findByTitle(role)
                .orElseThrow(() -> new BadRequestException("Role not found: " + role));

        assignRoleIfMissing(user, roleDB);

        String accessToken = jwtUtil.generateJwtToken(user.getUsername(), role);

        RefreshToken refreshTokenEntity =
                createRefreshTokenEntity(user.getUsername(), role, userDTO.getDeviceId(), userDTO.getDeviceName());

        AuthDTO response = new AuthDTO();
        userDTO.setId(user.getId());
        userDTO.setPassword(null);

        response.setUserDTO(userService.getUserDTOByUsername(user.getUsername(), role));
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshTokenEntity.getToken());

        return response;
    }

    public AuthDTO login(AuthDTO authDTO, ERole requiredRole) {

        Validator.validateLogin(authDTO);
        authDTO.setUsername(normalizeEmail(authDTO.getUsername()));

        User user = userService.getUserByUsername(authDTO.getUsername());

        /*
         * OAuth-only accounts (e.g. created via Google) have no local password.
         * Reject password login for them with a clear message.
         */
        if (CommonMethods.isEmpty(user.getPassword())) {
            throw new BadRequestException("This account uses Google sign-in. Please continue with Google.");
        }

        if (!passwordEncoder.matches(authDTO.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid credentials");
        }

        assertAccountActive(user);

        /*
         * A user may hold multiple roles (customer + seller). Verify they actually
         * hold the role for THIS portal instead of trusting an arbitrary single role.
         */
        Role role = requireActiveRole(user, requiredRole);

        String accessToken = jwtUtil.generateJwtToken(user.getUsername(), role.getTitle());

        RefreshToken refreshTokenEntity =
                createRefreshTokenEntity(user.getUsername(), role.getTitle(), authDTO.getDeviceId(), authDTO.getDeviceName());

        AuthDTO response = new AuthDTO();
        response.setUserDTO(userService.getUserDTOByUsername(user.getUsername(), role.getTitle()));
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshTokenEntity.getToken());

        return response;
    }

    @Override
    public AuthDTO refreshToken(String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Invalid authorization header");
        }

        String token = authHeader.substring(7);

        // Validate refresh token existence
        RefreshToken refreshToken = getRefreshTokenAndRevokedFalse(token);

        // Validate expiry
        if (!refreshToken.getExpiryDate().isAfter(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidTokenException("Refresh token expired");
        }

        // Load user
        User user = userService.getUserByUsername(refreshToken.getUsername());

        assertAccountActive(user);

        // Preserve the role the token was originally issued for. Tokens created
        // before role binding are deliberately rejected rather than guessing a
        // role for a multi-role account.
        ERole role = refreshToken.getRole();
        if (role == null) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new InvalidTokenException("Refresh token must be renewed by signing in again");
        }
        // Ensure the user still holds this role and it is active.
        requireActiveRole(user, role);

        // Generate new access token with the same role
        String newAccessToken = jwtUtil.generateJwtToken(user.getUsername(), role);

        // Rotate the refresh token (revoke old, issue new) keeping device + role
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        RefreshToken newRefreshToken =
                createRefreshTokenEntity(user.getUsername(), role, refreshToken.getDeviceId(), refreshToken.getDeviceName());

        AuthDTO authDTO = new AuthDTO();
        authDTO.setUserDTO(userService.getUserDTOByUsername(user.getUsername(), role));
        authDTO.setAccessToken(newAccessToken);
        authDTO.setRefreshToken(newRefreshToken.getToken());

        return authDTO;
    }

    @Override
    public String logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        RefreshToken refreshToken = getRefreshTokenAndRevokedFalse(token);
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return "Logged out successfully";
    }

    /*
     * =============================================================
     * Google OAuth2 provisioning
     * =============================================================
     */

    @Override
    public AuthDTO processOAuth2Login(OAuth2User googleUser,
                                      ERole requestedRole,
                                      String deviceId,
                                      String deviceName) {

        String providerId = googleUser.getAttribute("sub");
        String email = normalizeEmail(googleUser.getAttribute("email"));
        String firstName = googleUser.getAttribute("given_name");
        String lastName = googleUser.getAttribute("family_name");
        String imageUrl = googleUser.getAttribute("picture");
        Boolean emailVerified = googleUser.getAttribute("email_verified");

        // Validate Google identity
        if (CommonMethods.isEmpty(providerId)) {
            throw new BadRequestException("Google account ID not found");
        }
        if (CommonMethods.isEmpty(email)) {
            throw new BadRequestException("Google account email not found");
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new BadRequestException("Google email is not verified");
        }

        // Find or create the Orbit user behind this Google identity
        User user = findOrCreateGoogleUser(providerId, email, firstName, lastName, imageUrl);

        assertAccountActive(user);

        /*
         * Ensure the user holds the role for the portal they signed in through.
         *
         * NOTE (product decision): Google sign-in on the seller portal GRANTS
         * ROLE_SELLER, mirroring the open local /signup/seller flow. To instead
         * require a pre-existing seller account, replace grantRole(...) below with
         * a thrown BadRequestException for ROLE_SELLER.
         */
        Role role = grantRole(user, requestedRole);

        String accessToken = jwtUtil.generateJwtToken(user.getUsername(), role.getTitle());

        RefreshToken refreshToken =
                createRefreshTokenEntity(user.getUsername(), role.getTitle(),
                        resolveDeviceId(deviceId), resolveDeviceName(deviceName, requestedRole));

        UserDTO userDTO = userService.getUserDTOByUsername(user.getUsername(), role.getTitle());

        AuthDTO authDTO = new AuthDTO();
        authDTO.setUserDTO(userDTO);
        authDTO.setAccessToken(accessToken);
        authDTO.setRefreshToken(refreshToken.getToken());

        return authDTO;
    }

    private User findOrCreateGoogleUser(String providerId, String email, String firstName,
                                        String lastName, String imageUrl) {

        // 1. Match by Google provider id
        Optional<User> byProvider =
                userRepository.findByProviderAndProviderId(EAuthProvider.GOOGLE, providerId);

        if (byProvider.isPresent()) {
            User user = byProvider.get();
            updateGoogleProfile(user, firstName, lastName, imageUrl);
            return userRepository.save(user);
        }

        // 2. Link to an existing account with the same (verified) email
        Optional<User> byEmail = userRepository.findByEmail(email);

        if (byEmail.isPresent()) {
            User user = byEmail.get();
            // Keep any existing password so the account can use both email+password and Google.
            user.setProvider(EAuthProvider.GOOGLE);
            user.setProviderId(providerId);
            updateGoogleProfile(user, firstName, lastName, imageUrl);
            return userRepository.save(user);
        }

        // 3. Create a fresh Google user
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setUsername(email);
        user.setPassword(null);
        user.setProvider(EAuthProvider.GOOGLE);
        user.setProviderId(providerId);
        user.setImageUrl(imageUrl);
        user.setStatus(EStatus.ACTIVE);

        return userRepository.save(user);
    }

    private void updateGoogleProfile(User user, String firstName, String lastName, String imageUrl) {
        if (!CommonMethods.isEmpty(firstName)) {
            user.setFirstName(firstName);
        }
        if (!CommonMethods.isEmpty(lastName)) {
            user.setLastName(lastName);
        }
        if (!CommonMethods.isEmpty(imageUrl)) {
            user.setImageUrl(imageUrl);
        }
    }

    /*
     * =============================================================
     * Role helpers
     * =============================================================
     */

    /**
     * Require that the user already holds the given role (active). Used by login.
     */
    private Role requireActiveRole(User user, ERole requiredRole) {
        return roleRepository
                .findRoleByUserIdAndRoleTitleAndStatus(user.getId(), requiredRole, EStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Account does not have " + requiredRole + " access"));
    }

    /**
     * Ensure the user holds the given role, granting it if missing. Used by OAuth sign-in.
     */
    private Role grantRole(User user, ERole requestedRole) {

        Optional<Role> existing = roleRepository
                .findRoleByUserIdAndRoleTitleAndStatus(user.getId(), requestedRole, EStatus.ACTIVE);

        if (existing.isPresent()) {
            return existing.get();
        }

        Role role = roleRepository.findByTitle(requestedRole)
                .orElseThrow(() -> new BadRequestException(requestedRole + " not found"));

        assignRoleIfMissing(user, role);

        return role;
    }

    private void assignRoleIfMissing(User user, Role role) {
        if (!userRolesRepository.existsByUserAndRole(user, role)) {
            UserRoles userRoles = new UserRoles();
            userRoles.setUser(user);
            userRoles.setRole(role);
            userRolesRepository.save(userRoles);
        }
    }

    private void assertAccountActive(User user) {
        if (user.getStatus() != EStatus.ACTIVE) {
            throw new BadRequestException("Account is " + user.getStatus() + ". Please contact support.");
        }
    }

    /*
     * =============================================================
     * Refresh token helpers
     * =============================================================
     */

    private RefreshToken createRefreshTokenEntity(String username, ERole role, String deviceId, String deviceName) {

        RefreshToken token = new RefreshToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUsername(username);
        token.setRole(role);
        token.setDeviceId(resolveDeviceId(deviceId));
        token.setDeviceName(CommonMethods.isEmpty(deviceName) ? "web-client" : deviceName);
        token.setExpiryDate(LocalDateTime.now().plusDays(7));
        token.setRevoked(false);

        return refreshTokenRepository.save(token);
    }

    public RefreshToken getRefreshTokenAndRevokedFalse(String token) {
        return refreshTokenRepository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new BadRequestException(
                        MyConstants.ERR_MSG_NOT_FOUND + "Refresh Token: " + token));
    }

    private String resolveDeviceId(String deviceId) {
        return CommonMethods.isEmpty(deviceId) ? "web-client" : deviceId;
    }

    private String resolveDeviceName(String deviceName, ERole role) {
        if (!CommonMethods.isEmpty(deviceName)) {
            return deviceName;
        }
        return role == ERole.ROLE_SELLER ? "Seller Google OAuth" : "Customer Google OAuth";
    }

    private String normalizeEmail(String email) {
        return CommonMethods.isEmpty(email) ? email : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

}
