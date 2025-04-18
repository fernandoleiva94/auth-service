package com.sevenb.auth.auth_service.repository;

import com.sevenb.auth.auth_service.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}