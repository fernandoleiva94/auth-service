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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    private static final String PLATFORM_PREFIX = "PLATFORM_";

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

    /**
     * Los códigos {@code PLATFORM_*} nunca se toman del rol: solo cuentan si vienen de un override
     * concedido al usuario.
     */
    public List<String> getEffectivePermissions(User user) {
        Set<String> permissions = new HashSet<>();
        if (user.getRole() != null) {
            user.getRole().getPermissions().stream()
                    .map(PermissionEntity::getCode)
                    .filter(code -> code != null && !code.startsWith(PLATFORM_PREFIX))
                    .forEach(permissions::add);
        }

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
