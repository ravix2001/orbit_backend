package com.ravi.orbit.service.impl;

import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.entity.RefreshToken;
import com.ravi.orbit.entity.Role;
import com.ravi.orbit.entity.User;
import com.ravi.orbit.entity.UserRoles;
import com.ravi.orbit.enums.ERole;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
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
//        if (CommonMethods.isEmpty(userDTO.getPhone())){
//            userDTO.setPhone("");
//        }
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

//        Validator.validateUserSignup(userDTO);

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

        userRepository.save(user);

        Role roleDB = roleRepository.findByTitle(role)
                .orElseThrow(() -> new BadRequestException("Role not found: " + role));

        UserRoles userRoles = new UserRoles();
        userRoles.setUser(user);
        userRoles.setRole(roleDB);
        userRolesRepository.save(userRoles);

        String accessToken = jwtUtil.generateJwtToken(user.getUsername(), role);

        RefreshToken refreshTokenEntity = createRefreshTokenEntity(user.getUsername(), userDTO.getDeviceId(), userDTO.getDeviceName());

        AuthDTO response = new AuthDTO();
        userDTO.setId(user.getId());
        userDTO.setPassword(null);

        response.setUserDTO(userDTO);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshTokenEntity.getToken());

        return response;
    }

    public AuthDTO login(AuthDTO authDTO, ERole requiredRole) {

        Validator.validateLogin(authDTO);

        User user = userService.getUserByUsername(authDTO.getUsername());

        if (!passwordEncoder.matches(authDTO.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid credentials");
        }

        Role role = roleRepository.findRoleByUsername(authDTO.getUsername())
                .orElseThrow(() -> new BadRequestException("Role not found"));

        if (!role.getTitle().equals(requiredRole)) {
            throw new BadRequestException("User does not have required role");
        }

        String accessToken = jwtUtil.generateJwtToken(authDTO.getUsername(), role.getTitle());

        RefreshToken refreshTokenEntity = createRefreshTokenEntity(authDTO.getUsername(), authDTO.getDeviceId(), authDTO.getDeviceName());

        AuthDTO response = new AuthDTO();
        response.setUserDTO(userService.getUserDTOByUsername(authDTO.getUsername()));
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
        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidTokenException("Refresh token expired");
        }


        // Load user + roles
        User user = userService.getUserByUsername(refreshToken.getUsername());

        Role role = roleRepository.findRoleByUsername(refreshToken.getUsername())
                .orElseThrow(() -> new BadRequestException(
                        MyConstants.ERR_MSG_NOT_FOUND + "Role of user with username: " + refreshToken.getUsername()));


        // Generate new access token WITH ROLES
        String newAccessToken =
                jwtUtil.generateJwtToken(user.getUsername(), role.getTitle());

        String deviceId = refreshToken.getDeviceId();
        String deviceName = refreshToken.getDeviceName();

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        RefreshToken newRefreshToken = createRefreshTokenEntity(user.getUsername(), deviceId, deviceName);

        AuthDTO authDTO = new AuthDTO();
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

    private String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    private RefreshToken createRefreshTokenEntity(String username, String deviceId, String deviceName) {

        RefreshToken token = new RefreshToken();
        token.setToken(generateRefreshToken());
        token.setUsername(username);
        token.setDeviceId(deviceId);
        token.setDeviceName(deviceName);
        token.setExpiryDate(LocalDateTime.now().plusDays(7));
        token.setRevoked(false);

        return refreshTokenRepository.save(token);
    }

//    public RefreshToken getRefreshToken(String token) {
//        return refreshTokenRepository.findByToken(token)
//                .orElseThrow(() -> new BadRequestException(
//                        MyConstants.ERR_MSG_NOT_FOUND + "Refresh Token: " + token));
//    }

    public RefreshToken getRefreshTokenAndRevokedFalse(String token) {
        return refreshTokenRepository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new BadRequestException(
                        MyConstants.ERR_MSG_NOT_FOUND + "Refresh Token: " + token));
    }

}
