package com.laboratory.auth.observatorio.api.dto;

public record ImportarError(String hoja, int fila, String campo, String mensaje) {
}
