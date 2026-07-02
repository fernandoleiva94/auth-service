package com.sevenb.auth.auth_service.controller;

import com.sevenb.auth.auth_service.entity.AuthRequest;
import com.sevenb.auth.auth_service.entity.AuthResponse;
import com.sevenb.auth.auth_service.entity.User;
import com.sevenb.auth.auth_service.service.JwtService;
import com.sevenb.auth.auth_service.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest authRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));

            User user = (User) authentication.getPrincipal();
            List<String> permissions = userService.getEffectivePermissions(user);
            return new AuthResponse(jwtService.generateToken(user, user.getPerson().getId(), permissions));
        } catch (AuthenticationException e) {
            throw new RuntimeException("Invalid credentials");
        }
    }
}
