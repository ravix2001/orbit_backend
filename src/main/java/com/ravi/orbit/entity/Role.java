package com.ravi.orbit.entity;

import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "role_tbl")
public class Role extends UIDBase {

    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "title", nullable = false)
    private ERole title = ERole.ROLE_USER;

    @Column(name = "color_code")
    private String colorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EStatus status = EStatus.ACTIVE;

}
