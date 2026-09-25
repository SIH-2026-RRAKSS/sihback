package com.sih.dataservice.users.repository;

import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmployeeId(String employeeId);
    Optional<User> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);
    Optional<User> findByPhoneHash(String phoneHash);
    Page<User> findByRole(UserRole role, Pageable pageable);
    List<User> findByRoleInAndStatus(Collection<UserRole> roles, UserStatus status);
    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);
}
