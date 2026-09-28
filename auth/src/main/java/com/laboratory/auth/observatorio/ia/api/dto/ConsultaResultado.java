package com.laboratory.auth.observatorio.ia.api.dto;

import java.util.List;

public record ConsultaResultado(
        String sql,
        List<String> columnas,
        List<List<Object>> filas
) {
    public boolean vacio() {
        return filas.isEmpty();
    }
}
