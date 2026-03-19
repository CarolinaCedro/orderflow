package org.cedro.ordersecurityserver.controller;

import org.cedro.ordersecurityserver.dto.LoginRequest;
import org.cedro.ordersecurityserver.dto.TokenResponse;
import org.cedro.ordersecurityserver.repository.AppUserRepository;
import org.cedro.ordersecurityserver.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@RequestBody LoginRequest request) {
        return appUserRepository.findByUsername(request.username())
                .filter(user -> user.isActive() && passwordEncoder.matches(request.password(), user.getPassword()))
                .map(user -> ResponseEntity.ok(new TokenResponse(tokenService.generateToken(user), 3600L)))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
