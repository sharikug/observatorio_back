package com.laboratory.auth.observatorio.api.dto;

/**
 * Una inconsistencia localizada. fila usa la numeracion de Excel (1 = primera fila).
 * Los problemas de hoja completa (hoja faltante, encabezados) llegan con fila 0 y sin celda.
 */
public record ValidacionInconsistencia(
        String hoja,
        int fila,
        String columna,
        String nombreColumna,
        String celda,
        TipoInconsistencia tipo,
        String mensaje,
        String sugerencia
) {
}
