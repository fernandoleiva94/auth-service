package com.sevenb.auth.auth_service.entity;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuthRequest {
    private String username;
    private String password;

}