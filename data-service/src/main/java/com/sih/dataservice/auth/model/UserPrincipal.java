package com.sih.dataservice.auth.model;

import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String username; // email or employeeId
    private final String password;
    private final String name;
    private final UserRole role;
    private final UUID bankId;
    private final String jurisdictionPath;
    private final int tokenVersion;
    private final UserStatus status;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String username, String password, String name,
                         UserRole role, UUID bankId, String jurisdictionPath,
                         int tokenVersion, UserStatus status) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
        this.bankId = bankId;
        this.jurisdictionPath = jurisdictionPath;
        this.tokenVersion = tokenVersion;
        this.status = status;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public static UserPrincipal fromUser(User user) {
        String principalUsername = user.getEmployeeId() != null ? user.getEmployeeId() : user.getEmail();
        UUID bankId = user.getBank() != null ? user.getBank().getId() : null;
        String jurisdictionPath = user.getJurisdiction() != null ? user.getJurisdiction().getPath() : null;

        return new UserPrincipal(
                user.getId(),
                principalUsername,
                user.getPasswordHash(),
                user.getName(),
                user.getRole(),
                bankId,
                jurisdictionPath,
                user.getTokenVersion(),
                user.getStatus()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    public UUID getBankId() {
        return bankId;
    }

    public String getJurisdictionPath() {
        return jurisdictionPath;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public UserStatus getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
