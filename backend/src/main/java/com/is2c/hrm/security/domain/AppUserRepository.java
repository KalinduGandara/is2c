package com.is2c.hrm.security.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

    @Query("select u from AppUser u where lower(u.username) = lower(?1)")
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByEmployeeId(UUID employeeId);

    List<AppUser> findByEmployeeIdIn(java.util.Collection<UUID> employeeIds);

    @Query("select distinct u from AppUser u join u.roles r where r.code = ?1 and u.active = true")
    List<AppUser> findActiveByRoleCode(String roleCode);
}
