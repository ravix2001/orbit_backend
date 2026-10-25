package com.ravi.orbit.service;

import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.entity.User;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IUserService {

    UserDTO handleUser(UserDTO userDTO);

    UserDTO updateProfile(UserDTO userDTO, String username);

    UserDTO getUserDTOById(UUID id);

    UserDTO getUserDTOByUsername(String username);

    UserDTO getUserDTOByUsername(String username, ERole activeRole);

    User getUserPrincipal();

//    UserDTO getUserAuthByUsername(String username);

    Page<UserDTO> getUsersByRoleAndStatus(ERole role, EStatus status, Pageable pageable);

    void deleteUser(String username);

    void deleteUserHard(UUID userId);

    User getUserById(UUID id);

    User getUserByUsername(String username);

    User getUserByEmail(String email);

    User mapToUserEntity (User user, UserDTO userDTO);

}
