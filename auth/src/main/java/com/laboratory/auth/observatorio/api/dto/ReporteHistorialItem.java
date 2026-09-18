package com.laboratory.auth.observatorio.api.dto;

public record ReporteHistorialItem(
        String id,
        String titulo,
        String tipo,
        String filtros,
        String fecha,
        String nombreArchivo
) {
}
