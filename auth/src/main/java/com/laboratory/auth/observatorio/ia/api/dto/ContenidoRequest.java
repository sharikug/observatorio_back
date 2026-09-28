package com.laboratory.auth.observatorio.ia.api.dto;

import java.util.List;

public record ContenidoRequest(
        String titulo,
        String url,
        String texto,
        List<String> roles
) {
}
