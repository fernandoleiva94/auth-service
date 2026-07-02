package com.sevenb.auth.auth_service.service;

import com.sevenb.auth.auth_service.entity.PermissionEntity;
import com.sevenb.auth.auth_service.entity.User;
import com.sevenb.auth.auth_service.entity.UserPermissionOverride;
import com.sevenb.auth.auth_service.repository.UserPermissionOverrideRepository;
import com.sevenb.auth.auth_service.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserPermissionOverrideRepository overrideRepository;

    public UserService(UserRepository userRepository, UserPermissionOverrideRepository overrideRepository) {
        this.userRepository = userRepository;
        this.overrideRepository = overrideRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public List<String> getEffectivePermissions(User user) {
        Set<String> permissions = user.getRole().getPermissions().stream()
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());

        List<UserPermissionOverride> overrides = overrideRepository.findByUserId(user.getId());
        for (UserPermissionOverride override : overrides) {
            if (override.isGranted()) {
                permissions.add(override.getPermissionCode());
            } else {
                permissions.remove(override.getPermissionCode());
            }
        }

        return permissions.stream().sorted().collect(Collectors.toList());
    }
}
