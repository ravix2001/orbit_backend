package com.ravi.orbit.controller;

import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import com.ravi.orbit.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    // Get all users
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(params = {"role", "status"} )
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @RequestParam ERole role, EStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "true") boolean ascending) {

        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(userService.getUsersByRoleAndStatus(role, status, pageable));
    }

    // Get userById
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserDTOById(id));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return ResponseEntity.ok(userService.getUserDTOByUsername(username, activeRole(authentication)));
    }

    @PutMapping("/update")
    public ResponseEntity<UserDTO> updateProfile(@RequestBody UserDTO userDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        UserDTO updatedUser = userService.updateProfile(userDto, username);
        updatedUser.setActiveRole(activeRole(authentication));
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        userService.deleteUser(username);
        return ResponseEntity.ok().build();
    }

    private ERole activeRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> ERole.valueOf(authority.getAuthority()))
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
    }

}
