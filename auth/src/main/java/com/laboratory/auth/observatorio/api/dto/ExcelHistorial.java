package com.laboratory.auth.observatorio.api.dto;

import java.util.List;

/** Vista del historial: el Excel activo y, aparte, los archivos anteriores. */
public record ExcelHistorial(ExcelCargadoItem activo, List<ExcelCargadoItem> historial) {

    public boolean hayActivo() {
        return activo != null;
    }
}
