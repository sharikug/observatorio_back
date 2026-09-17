package com.laboratory.auth.observatorio.api.dto;

public record ColaboracionGrupo(
        String project,
        String origin,
        String originName,
        String target,
        String targetName
) {
}
