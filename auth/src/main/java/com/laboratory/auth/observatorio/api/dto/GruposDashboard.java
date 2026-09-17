package com.laboratory.auth.observatorio.api.dto;

import java.util.List;

public record GruposDashboard(
        List<GrupoInfo> grupos,
        List<GrupoProyecto> proyectos,
        List<ColaboracionGrupo> colaboraciones
) {
}
