package com.laboratory.auth.observatorio.ia.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * HU-10: la auditoria es la que permite reconstruir querespondio el asistente. Si el
 * INSERT falla, el chat entero falla con 500, asi que se verifica que el SQL y los
 * argumentos siguenadrn en correspondencia.
 */
class AuditoriaServiceTest {

    @Test
    void elRegistroDeAuditoriaNoPierdeArgumentos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Object[]> enviadas = new ArrayList<>();
        doAnswer(inv -> {
            enviadas.add(inv.getArguments());
            return 1;
        }).when(jdbc).update(anyString(), any(Object[].class));

        new AuditoriaService(jdbc).registrar("u@ucundinamarca.edu.co", "DIRECTIVO",
                "Cuantos grupos hay?", List.of(), "SELECT 1", "49 grupos", true, 120);

        assertEquals(1, enviadas.size());
        Object[] llamada = enviadas.get(0);
        String sql = (String) llamada[0];
        assertEquals(llamada.length - 1, sql.chars().filter(c -> c == '?').count(),
                "marcadores y argumentos no coinciden en: " + sql);
    }
}
