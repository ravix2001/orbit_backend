package com.ravi.orbit.controller;

import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final IUserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return ResponseEntity.ok(userService.getUserDTOByUsername(username, ERole.ROLE_SELLER));
    }

    @PutMapping("/update")
    public ResponseEntity<UserDTO> updateProfile(@RequestBody UserDTO userDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        UserDTO updatedUser = userService.updateProfile(userDto, username);
        updatedUser.setActiveRole(ERole.ROLE_SELLER);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        userService.deleteUser(username);
        return ResponseEntity.ok().build();
    }

}
