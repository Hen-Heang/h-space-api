package com.example.jwt_authentication.controller;

import com.example.jwt_authentication.domain.user.User;
import com.example.jwt_authentication.payload.LoginRequest;
import com.example.jwt_authentication.payload.LoginResponse;
import com.example.jwt_authentication.payload.RegisterRequest;
import com.example.jwt_authentication.service.user.AuthenticationService;
import com.example.jwt_authentication.service.user.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController extends AbstractRestController {
    private final JwtService jwtService;
    private final AuthenticationService authenticationService;

    @PostMapping("/signup")
    public ResponseEntity<User> register(@RequestBody RegisterRequest registerRequest) {
        User user = authenticationService.signup(registerRequest);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginRequest loginRequest) {
        User userLogin = authenticationService.authenticate(loginRequest);
        String jwtToken = jwtService.generateToken(userLogin);
        LoginResponse loginResponse = new LoginResponse().setToken(jwtToken).setExpiresIn(jwtService.getExpirationTime());
        return ResponseEntity.ok(loginResponse);
    }
}
