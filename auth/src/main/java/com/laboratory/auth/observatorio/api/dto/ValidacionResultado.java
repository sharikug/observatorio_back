package com.laboratory.auth.observatorio.api.dto;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Respuesta de POST /api/observatorio/importar/validar.
 *
 * <p>valido = no se encontro ninguna inconsistencia.
 * puedeContinuar = no hay inconsistencias criticas, asi que el usuario puede elegir
 * "Continuar de todos modos" (las advertencias se aceptan bajo su responsabilidad).
 */
public record ValidacionResultado(
        String archivo,
        boolean valido,
        boolean puedeContinuar,
        int totalInconsistencias,
        int criticas,
        int advertencias,
        List<ValidacionHoja> hojas,
        List<ValidacionInconsistencia> inconsistencias
) {

    /** Texto corto para el 400 de /importar cuando el archivo tiene errores criticos. */
    public String resumenCriticos() {
        return inconsistencias.stream()
                .filter(i -> i.tipo().critico())
                .limit(3)
                .map(i -> i.hoja() + (i.celda().isEmpty() ? "" : " celda " + i.celda()) + ": " + i.mensaje())
                .collect(Collectors.joining("; "));
    }
}
