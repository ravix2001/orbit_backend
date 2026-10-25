package com.ravi.orbit.entity;

import com.ravi.orbit.enums.ERole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "refresh_token_tbl")
public class RefreshToken extends UIDBase {

    private static final long serialVersionUID = 1L;

    @Column(name = "token", nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "username", nullable = false)
    private String username;

    /*
     * The role/portal this token was issued for.
     *
     * A user may hold multiple roles (e.g. ROLE_USER + ROLE_SELLER).
     * Persisting the role here lets a refresh reissue an access token
     * for the SAME role instead of an arbitrary one.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private ERole role;

    @Column(name = "expiryDate", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "revoked")
    private boolean revoked = false;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

}
