package com.laboratory.auth.observatorio.ia.api.dto;

import java.util.List;

public record ChatResponse(
        String respuesta,
        boolean enAlcance,
        List<String> fuentes,
        String sql,
        long latenciaMs,
        /** Excel ACTIVE del que proviene la respuesta, para que el usuario la vea. */
        String fuenteDatos,
        /** HU-17: conversacion en la que quedo guardada esta respuesta, para continuarla. */
        String conversacionId
) {
}
