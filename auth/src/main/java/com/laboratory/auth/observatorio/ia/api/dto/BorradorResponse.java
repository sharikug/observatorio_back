package com.laboratory.auth.observatorio.ia.api.dto;

import java.util.List;

public record BorradorResponse(
        String borrador,
        List<String> fuentes,
        String leyenda
) {
}
