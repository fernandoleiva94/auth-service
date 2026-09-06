package com.sevenb.auth.auth_service.service;

import com.sevenb.auth.auth_service.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String SECRET_KEY = "my-secret-key-which-should-be-very-long-and-secure";

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    public String generateToken(User user, Long userId, List<String> permissions) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("id", userId)
                .claim("name", buildDisplayName(user))
                .claim("user", user.getUsername())
                .claim("ownerId", user.getPerson().getId())
                .claim("tenantId", user.getPerson().getTenantId())
                .claim("taxCondition", user.getPerson().getTaxCondition())
                .claim("role", user.getRole().getName())
                .claim("permissions", permissions)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 5))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildDisplayName(User user) {
        if (user == null) {
            return null;
        }

        String username = user.getUsername();
        if (user.getPerson() == null) {
            return username;
        }

        String firstName = normalize(user.getPerson().getFirstName());
        String lastName = normalize(user.getPerson().getLastName());

        if (firstName.isEmpty() && lastName.isEmpty()) {
            return username;
        }
        if (firstName.isEmpty()) {
            return lastName;
        }
        if (lastName.isEmpty()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
