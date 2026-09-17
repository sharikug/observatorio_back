package com.laboratory.auth.observatorio.api.dto;

import java.util.List;

public record GrupoProyecto(
        String id,
        Integer year,
        String conv,
        List<ParticipanteGrupo> participants,
        List<GrupoEnProyecto> groups
) {
}
