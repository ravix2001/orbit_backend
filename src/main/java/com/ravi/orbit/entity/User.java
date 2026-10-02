package com.ravi.orbit.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ravi.orbit.enums.EAuthProvider;
import com.ravi.orbit.enums.EStatus;
import com.ravi.orbit.enums.EGender;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "user_tbl",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                )
        })
public class User extends UIDBase {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "phone", unique = true)
    private String phone;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private EGender gender;

    @Column(name = "dob")
    private LocalDate dob;

    // provider
    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private EAuthProvider provider = EAuthProvider.LOCAL;

    // External id from Google/GitHub, null for LOCAL accounts
    @Column(name = "provider_id")
    private String providerId;

    // status
    @Enumerated(EnumType.STRING)
    @Column(name = "user_status")
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "image_url")
    private String imageUrl;

    // address
    @Column(name = "address")
    private String address;

    @Column(name = "zipcode")
    private String zipcode;

    @Column(name = "state")
    private String state;

    @Column(name = "country_code")
    private String countryCode;

}
