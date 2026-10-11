package com.sevenb.auth.auth_service.service;

import com.sevenb.auth.auth_service.entity.PermissionEntity;
import com.sevenb.auth.auth_service.entity.Role;
import com.sevenb.auth.auth_service.entity.User;
import com.sevenb.auth.auth_service.entity.UserPermissionOverride;
import com.sevenb.auth.auth_service.repository.UserPermissionOverrideRepository;
import com.sevenb.auth.auth_service.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserPermissionOverrideRepository overrideRepository = mock(UserPermissionOverrideRepository.class);
    private final UserService userService = new UserService(mock(UserRepository.class), overrideRepository);

    private static PermissionEntity permission(String code) {
        PermissionEntity p = new PermissionEntity();
        p.setCode(code);
        return p;
    }

    private static UserPermissionOverride override(String code, boolean granted) {
        UserPermissionOverride o = new UserPermissionOverride();
        o.setPermissionCode(code);
        o.setGranted(granted);
        return o;
    }

    @Test
    void getEffectivePermissions_appliesOverridesOverRolePermissions() {
        Role role = new Role();
        role.setName("CAJERO");
        role.setPermissions(Set.of(permission("SALES_CREATE"), permission("SALES_VIEW")));

        User user = new User();
        user.setId(5L);
        user.setRole(role);
        when(overrideRepository.findByUserId(5L))
                .thenReturn(List.of(override("SALES_VIEW", false), override("STOCK_VIEW", true)));

        assertEquals(List.of("SALES_CREATE", "STOCK_VIEW"), userService.getEffectivePermissions(user));
    }

    @Test
    void getEffectivePermissions_withoutRole_returnsOnlyGrantedOverrides() {
        User user = new User();
        user.setId(6L);
        when(overrideRepository.findByUserId(6L))
                .thenReturn(List.of(override("SALES_VIEW", true), override("USERS_MANAGE", false)));

        assertEquals(List.of("SALES_VIEW"), userService.getEffectivePermissions(user));
    }

    @Test
    void getEffectivePermissions_ignoresPlatformCodesFromRole() {
        Role role = new Role();
        role.setName("ADMIN");
        role.setPermissions(Set.of(permission("USERS_MANAGE"), permission("PLATFORM_ADMIN")));

        User user = new User();
        user.setId(7L);
        user.setRole(role);
        when(overrideRepository.findByUserId(7L)).thenReturn(List.of());

        assertEquals(List.of("USERS_MANAGE"), userService.getEffectivePermissions(user));
    }

    @Test
    void getEffectivePermissions_includesPlatformCodesFromGrantedOverride() {
        Role role = new Role();
        role.setName("ADMIN");
        role.setPermissions(Set.of(permission("USERS_MANAGE")));

        User user = new User();
        user.setId(8L);
        user.setRole(role);
        when(overrideRepository.findByUserId(8L)).thenReturn(List.of(override("PLATFORM_ADMIN", true)));

        assertEquals(List.of("PLATFORM_ADMIN", "USERS_MANAGE"), userService.getEffectivePermissions(user));
    }

    @Test
    void getEffectivePermissions_revokedPlatformOverrideIsNotIncluded() {
        Role role = new Role();
        role.setName("ADMIN");
        role.setPermissions(Set.of(permission("USERS_MANAGE"), permission("PLATFORM_ADMIN")));

        User user = new User();
        user.setId(9L);
        user.setRole(role);
        when(overrideRepository.findByUserId(9L)).thenReturn(List.of(override("PLATFORM_ADMIN", false)));

        assertEquals(List.of("USERS_MANAGE"), userService.getEffectivePermissions(user));
    }
}
