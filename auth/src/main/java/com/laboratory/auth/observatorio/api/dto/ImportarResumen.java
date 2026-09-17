package com.laboratory.auth.observatorio.api.dto;

import java.util.List;

public record ImportarResumen(
        int creados,
        int actualizados,
        int omitidos,
        List<ImportarError> errores
) {
}
