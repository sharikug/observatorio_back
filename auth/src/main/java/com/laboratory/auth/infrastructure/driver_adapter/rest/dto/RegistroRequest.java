package com.laboratory.auth.infrastructure.driver_adapter.rest.dto;

/**
 * El rol no viene aqui a proposito: el cliente no puede elegirlo. Solo envia
 * {@code codigoAdmin} y el backend decide si el registro es de estudiante o de
 * administrador.
 */
public record RegistroRequest(
        String idcard,
        String name,
        String lastname,
        String email,
        String password,
        String phone,
        String codigoAdmin
) {
}