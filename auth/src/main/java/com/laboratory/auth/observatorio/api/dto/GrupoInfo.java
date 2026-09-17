package com.laboratory.auth.observatorio.api.dto;

import java.util.List;

public record GrupoInfo(
        String id,
        String display,
        String full,
        String leader,
        String faculty,
        String regional,
        List<String> programs
) {
}
