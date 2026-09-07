package com.laboratory.auth.infrastructure.driver_adapter.rest.dto;

public record RegistroRequest(
        String idcard,
        String name,
        String lastname,
        String email,
        String password,
        String phone,
        String rol
) {
}