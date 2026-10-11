package com.sevenb.auth.auth_service.service;

import com.sevenb.auth.auth_service.entity.Person;
import com.sevenb.auth.auth_service.entity.Role;
import com.sevenb.auth.auth_service.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET_KEY = "my-secret-key-which-should-be-very-long-and-secure";

    @Test
    void generateToken_shouldIncludeTenantIdClaim() {
        JwtService jwtService = new JwtService();

        Person person = new Person();
        person.setId(99L);
        person.setFirstName("Fernando");
        person.setLastName("Perez");
        person.setTenantId("tenant-acme");
        person.setTaxCondition("RESPONSABLE_INSCRIPTO");

        Role role = new Role();
        role.setId(1L);
        role.setName("ADMIN");

        User user = new User();
        user.setUsername("fernando");
        user.setPerson(person);
        user.setRole(role);

        String token = jwtService.generateToken(user, person.getId(), List.of());

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals("tenant-acme", claims.get("tenantId", String.class));
        assertEquals("RESPONSABLE_INSCRIPTO", claims.get("taxCondition", String.class));
    }

    @Test
    void generateToken_shouldAvoidNullInNameClaimWhenLastNameIsMissing() {
        JwtService jwtService = new JwtService();

        Person person = new Person();
        person.setId(100L);
        person.setFirstName("Marcelo Raul Leiva");
        person.setLastName(null);
        person.setTenantId("tenant-abc");
        person.setTaxCondition("RESPONSABLE_INSCRIPTO");

        Role role = new Role();
        role.setId(2L);
        role.setName("ADMIN");

        User user = new User();
        user.setUsername("maspolloadmin");
        user.setPerson(person);
        user.setRole(role);

        String token = jwtService.generateToken(user, person.getId(), List.of());

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals("Marcelo Raul Leiva", claims.get("name", String.class));
    }

    @Test
    void generateToken_shouldIncludeRoleNameAndPermissionsList() {
        JwtService jwtService = new JwtService();

        Person person = new Person();
        person.setId(101L);
        person.setTenantId("tenant-xyz");

        Role role = new Role();
        role.setId(3L);
        role.setName("CAJERO");

        User user = new User();
        user.setUsername("cajero1");
        user.setPerson(person);
        user.setRole(role);

        String token = jwtService.generateToken(user, person.getId(), List.of("SALES_CREATE", "SALES_VIEW"));

        Claims claims = parse(token);

        assertEquals("CAJERO", claims.get("role", String.class));
        assertEquals(List.of("SALES_CREATE", "SALES_VIEW"), claims.get("permissions", List.class));
    }

    @Test
    void generateToken_shouldIncludeEmptyPermissionsListAsEmptyNotAbsent() {
        JwtService jwtService = new JwtService();

        Person person = new Person();
        person.setId(102L);
        person.setTenantId("tenant-xyz");

        Role role = new Role();
        role.setName("SIN_PERMISOS");

        User user = new User();
        user.setUsername("vacio");
        user.setPerson(person);
        user.setRole(role);

        Claims claims = parse(jwtService.generateToken(user, person.getId(), List.of()));

        assertTrue(claims.containsKey("permissions"));
        assertEquals(List.of(), claims.get("permissions", List.class));
    }

    @Test
    void generateToken_withoutRole_shouldOmitRoleClaimAndKeepPermissions() {
        JwtService jwtService = new JwtService();

        Person person = new Person();
        person.setId(103L);
        person.setTenantId("tenant-xyz");

        User user = new User();
        user.setUsername("sinrol");
        user.setPerson(person);

        Claims claims = parse(jwtService.generateToken(user, person.getId(), List.of("SALES_VIEW")));

        assertNull(claims.get("role"));
        assertEquals(List.of("SALES_VIEW"), claims.get("permissions", List.class));
        assertEquals("tenant-xyz", claims.get("tenantId", String.class));
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
