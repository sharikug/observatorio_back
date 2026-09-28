package com.laboratory.auth.observatorio.ia.api.dto;

public record FuenteRecuperada(
        String idDocumento,
        String documento,
        String referencia,
        String contenido,
        double score
) {
    public String cita() {
        return documento + (referencia == null || referencia.isBlank() ? "" : " (" + referencia + ")");
    }
}
