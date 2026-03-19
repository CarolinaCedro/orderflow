package org.cedro.ordersecurityserver.dto;

public record TokenResponse(String token, long expiresIn) {
}
