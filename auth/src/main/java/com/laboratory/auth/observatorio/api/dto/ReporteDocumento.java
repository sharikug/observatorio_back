package com.laboratory.auth.observatorio.api.dto;

import java.util.List;

public record ReporteDocumento(
        String tablero,
        List<String> filtrosAplicados,
        List<ReporteIndicador> indicadores,
        List<String> columnas,
        List<List<String>> filas,
        List<ReporteGrafico> graficos,
        String fuente
) {
}
