package com.laboratory.auth.infrastructure.driver_adapter.rest.dto;

public record LoginRequest(
        String email,
        String password
) {
}