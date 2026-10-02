package com.ravi.orbit.controller;

import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.service.IAuthService;
import com.ravi.orbit.utils.MyConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    @GetMapping("/google")
    public void googleLogin(@RequestParam String deviceId, @RequestParam String deviceName,
                            HttpSession session, HttpServletResponse response) throws IOException {

        session.setAttribute(MyConstants.OAUTH_DEVICE_ID, deviceId);
        session.setAttribute(MyConstants.OAUTH_DEVICE_NAME, deviceName);

        response.sendRedirect("/oauth2/authorization/google");
    }

    /* ===================== SIGNUP ===================== */

    @PostMapping("/signup/user")
    public ResponseEntity<AuthDTO> userSignup(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(authService.userSignup(userDTO));
    }

    @PostMapping("/signup/seller")
    public ResponseEntity<AuthDTO> sellerSignup(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(authService.sellerSignup(userDTO));
    }

    /* ===================== LOGIN ===================== */

    @PostMapping("/login/user")
    public ResponseEntity<AuthDTO> userLogin(@RequestBody AuthDTO request) {
        return ResponseEntity.ok(authService.userLogin(request));
    }

    @PostMapping("/login/seller")
    public ResponseEntity<AuthDTO> sellerLogin(@RequestBody AuthDTO request) {
        return ResponseEntity.ok(authService.sellerLogin(request));
    }

    @PostMapping("/login/admin")
    public ResponseEntity<AuthDTO> adminLogin(@RequestBody AuthDTO request) {
        return ResponseEntity.ok(authService.adminLogin(request));
    }

    /* ===================== TOKEN ===================== */

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthDTO> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.refreshToken(authHeader));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.logout(authHeader));
    }

}