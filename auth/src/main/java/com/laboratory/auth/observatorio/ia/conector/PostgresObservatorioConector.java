package com.laboratory.auth.observatorio.ia.conector;

import com.laboratory.auth.observatorio.ia.service.TextToSqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conector sobre las tablas del Observatorio en PostgreSQL (solo lectura). */
@Component
@RequiredArgsConstructor
public class PostgresObservatorioConector implements ConectorFuente {

    private final TextToSqlService textToSqlService;

    @Override
    public String id() {
        return "postgres-observatorio";
    }

    @Override
    public String descripcion() {
        return "Datos estructurados del Observatorio (proyectos, grupos, investigadores).";
    }

    @Override
    public List<String> tablas() {
        return List.copyOf(textToSqlService.tablasPermitidas());
    }
}
