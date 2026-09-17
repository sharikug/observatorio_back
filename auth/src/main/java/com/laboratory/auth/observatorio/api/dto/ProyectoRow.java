package com.laboratory.auth.observatorio.api.dto;

import java.util.List;

public record ProyectoRow(
        Integer year,
        String period,
        String conv,
        String code,
        String project,
        String pi,
        String regional,
        String faculty,
        String program,
        String researchType,
        String convenio,
        Integer teamSize,
        List<String> lines,
        List<Integer> ods,
        String objective
) {
}
