package com.laboratory.auth.infrastructure.driver_adapter.rest.dto;

public record AuthResponse(
        String token,
        String name,
        String rol
) {
}