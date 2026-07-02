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
    }
}

