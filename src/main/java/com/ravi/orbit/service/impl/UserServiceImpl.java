package com.ravi.orbit.service.impl;

import com.ravi.orbit.dto.UserDTO;
import com.ravi.orbit.entity.User;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import com.ravi.orbit.exceptions.BadRequestException;
import com.ravi.orbit.repository.UserRepository;
import com.ravi.orbit.service.IUserService;
import com.ravi.orbit.utils.CommonMethods;
import com.ravi.orbit.utils.MyConstants;
import com.ravi.orbit.utils.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;

    @Override
    public UserDTO handleUser(UserDTO userDTO) {

        Validator.validateSellerSignup(userDTO);

        User user = null;

        if(CommonMethods.isEmpty(userDTO.getId())){
            user = new User();
        }
        else{
            user = getUserById(userDTO.getId());
        }

        userRepository.save(mapToUserEntity(user, userDTO));

        userDTO.setId(user.getId());
        return userDTO;
    }

    @Override
    public UserDTO updateProfile(UserDTO userDTO, String username) {
        User user = getUserByUsername(username);
        mapToUserEntity(user, userDTO);
        userRepository.save(user);
        userDTO.setId(user.getId());
        userDTO.setPassword(null);
        return userDTO;
    }

    @Override
    public UserDTO getUserDTOById(UUID id) {
        return userRepository.getUserDTOById(id)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "User: " + id));
    }

    @Override
    public UserDTO getUserDTOByUsername(String username) {
        return userRepository.getUserDTOByUsername(username)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "User: " + username));
    }

    @Override
    public User getUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return getUserByUsername(username);
    }

//    @Override
//    public UserDTO getUserAuthByUsername(String username) {
//        return userRepository.getAuthByUsername(username)
//                .orElseThrow(() -> new BadRequestException(MyConstants
//                        .ERR_MSG_NOT_FOUND + "User: " + username));
//    }

    @Override
    public Page<UserDTO> getUsersByRoleAndStatus(ERole role, EStatus status, Pageable pageable) {
        return userRepository.getUsersByRoleAndStatus(role, status, pageable);
    }

    @Override
    public void deleteUser(String username) {
        User user = getUserByUsername(username);
        user.setStatus(EStatus.DELETED);
        userRepository.save(user);
    }

    // remaining to delete its children
    @Override
    public void deleteUserHard(UUID userId) {
        User user = getUserById(userId);
        userRepository.delete(user);
    }

    @Override
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "User: " + userId));
    }

    @Override
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("User not found with username: " + username));
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "User: " + email));
    }

    public User mapToUserEntity (User user, UserDTO userDTO) {
        user.setFirstName(userDTO.getFirstName());
        user.setMiddleName(userDTO.getMiddleName());
        user.setLastName(userDTO.getLastName());
        user.setUsername(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setEmail(userDTO.getEmail());
        user.setGender(userDTO.getGender());
        user.setDob(userDTO.getDob());
        user.setImageUrl(userDTO.getImageUrl());
        // address
        user.setAddress(userDTO.getAddress());
        user.setZipcode(userDTO.getZipcode());
        user.setState(userDTO.getState());
        user.setCountryCode(userDTO.getCountryCode());

        return user;
    }

}
