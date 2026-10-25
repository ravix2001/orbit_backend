package com.ravi.orbit.repository;

import com.ravi.orbit.dto.RoleDTO;
import com.ravi.orbit.entity.Role;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByTitle(ERole title);

    @Query("SELECT ur.role " +
            "FROM UserRoles ur " +
            "JOIN ur.user u " +
            "WHERE u.username = :username")
    Optional<Role> findRoleByUsername(String username);

    @Query("SELECT NEW com.ravi.orbit.dto.RoleDTO(r.id, r.title, r.colorCode, r.status) " +
            "FROM Role r")
    List<RoleDTO> getAllRoles();

    @Query("SELECT NEW com.ravi.orbit.dto.RoleDTO(r.id, r.title, r.colorCode, r.status) " +
            "FROM Role r " +
            "WHERE r.id = :id")
    Optional<RoleDTO> getRoleDTOById(UUID id);

    @Query("SELECT NEW com.ravi.orbit.dto.RoleDTO(r.id, r.title, r.colorCode, r.status) " +
            "FROM Role r " +
            "WHERE r.title = :title")
    Optional<RoleDTO> getRoleDTOByTitle(ERole title);

    @Query("SELECT r " +
            "FROM UserRoles ur " +
            "JOIN ur.role r " +
            "WHERE ur.userId = :userId AND r.title = :roleTitle AND r.status = :status")
    Optional<Role> findRoleByUserIdAndRoleTitleAndStatus(UUID userId, ERole roleTitle, EStatus status);

    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END " +
            "FROM UserRoles ur JOIN ur.role r " +
            "WHERE ur.userId = :userId AND r.title = :roleTitle AND r.status = :status")
    boolean existsRoleByUserIdAndRoleTitleAndStatus(UUID userId, ERole roleTitle, EStatus status);

    @Query("SELECT r.title FROM UserRoles ur JOIN ur.role r " +
            "WHERE ur.userId = :userId AND r.status = :status ORDER BY r.title")
    List<ERole> findRoleTitlesByUserIdAndStatus(UUID userId, EStatus status);

}
