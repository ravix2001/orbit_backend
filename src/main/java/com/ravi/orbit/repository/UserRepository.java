package com.ravi.orbit.repository;

import com.ravi.orbit.entity.User;
import com.ravi.orbit.enums.EAuthProvider;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.status = :status AND EXISTS " +
            "(SELECT 1 FROM UserRoles ur JOIN ur.role r " +
            "WHERE ur.user = u AND r.title = :role AND r.status = :roleStatus)")
    Page<User> getUsersByRoleAndStatus(ERole role, EStatus status, EStatus roleStatus, Pageable pageable);

//    @Query("SELECT NEW com.ravi.orbit.dto.UserDTO(u.username, u.password) " +
//            " FROM User u " +
//            " WHERE u.username = :username ")
//    Optional<UserDTO> getAuthByUsername(String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(EAuthProvider provider, String providerId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
