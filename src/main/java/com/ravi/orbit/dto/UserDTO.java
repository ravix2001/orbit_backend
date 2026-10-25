package com.ravi.orbit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ravi.orbit.enums.EGender;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {

    public UserDTO(String username, String password, String deviceId, String deviceName) {
        this.username = username;
        this.password = password;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }

    private UUID id;

    private String firstName;

    private String middleName;

    private String lastName;

    private String phone;

    private String email;

    private String username;

    private String password;

    private EGender gender;

    private LocalDate dob;

    /** All active roles assigned through user_roles_tbl. */
    private List<ERole> roles;

    /** The role carried by the current access token, when this response is auth-scoped. */
    private ERole activeRole;

    private EStatus status;

    private String imageUrl;

    // address
    private String address;

    private String zipcode;

    private String state;

    private String countryCode;

    // extra for auth
    private String deviceId;
    private String deviceName;

}
