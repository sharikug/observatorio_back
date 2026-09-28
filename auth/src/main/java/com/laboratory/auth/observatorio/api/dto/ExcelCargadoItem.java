package com.laboratory.auth.observatorio.api.dto;

/** Un Excel cargado en el sistema. Solo el que tiene activo=true alimenta dashboards e IA. */
public record ExcelCargadoItem(
        String id,
        String nombre,
        long tamano,
        String estado,
        String fechaCarga,
        String fechaHistorico,
        String usuario,
        boolean valido,
        int criticas,
        int advertencias,
        int totalInconsistencias,
        boolean activo
) {
}
