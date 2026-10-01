package com.laboratory.auth.observatorio.ia.api.dto;

import java.util.List;

/**
 * HU-07: el navegador reenvia el borrador ya redactado para convertirlo a PDF o Word.
 * No se regenera con el modelo: descargar no debe volver a pagar la llamada ni cambiar
 * el texto que el usuario ya leyo y valido.
 */
public record ExportarBorradorRequest(
        String tema,
        String borrador,
        List<String> fuentes,
        String leyenda
) {
}
