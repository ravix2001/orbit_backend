package com.ravi.orbit.repository;

import com.ravi.orbit.entity.Role;
import com.ravi.orbit.entity.User;
import com.ravi.orbit.entity.UserRoles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRolesRepository extends JpaRepository<UserRoles, UUID> {

    boolean existsByUserAndRole(User user, Role role);

    Optional<UserRoles> findByUser(User user);

}
