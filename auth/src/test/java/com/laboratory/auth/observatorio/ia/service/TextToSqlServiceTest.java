package com.laboratory.auth.observatorio.ia.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TextToSqlServiceTest {

    private TextToSqlService servicio() {
        return new TextToSqlService(null, null) {
            @Override
            public Set<String> tablasPermitidas() {
                return Set.of("proyecto", "grupo");
            }

            @Override
            public String descripcionEsquema() {
                return "proyecto(id_proyecto, anio); grupo(id_grupo, nombre_grupo)";
            }
        };
    }

    @Test
    void aceptaSelectDeListaBlanca() {
        servicio().validar("SELECT COUNT(*) FROM proyecto WHERE anio = 2024");
    }

    @Test
    void aceptaWithYJoin() {
        servicio().validar("WITH t AS (SELECT id_grupo FROM grupo) "
                + "SELECT p.id_proyecto FROM proyecto p JOIN t ON t.id_grupo = p.id_proyecto");
    }

    @Test
    void rechazaEscrituras() {
        TextToSqlService s = servicio();
        assertThrows(IllegalArgumentException.class, () -> s.validar("DELETE FROM proyecto"));
        assertThrows(IllegalArgumentException.class, () -> s.validar("UPDATE proyecto SET anio = 0"));
        assertThrows(IllegalArgumentException.class, () -> s.validar("DROP TABLE proyecto"));
        assertThrows(IllegalArgumentException.class, () -> s.validar("SELECT * FROM proyecto; DROP TABLE grupo"));
    }

    @Test
    void rechazaTablaFueraDeListaBlanca() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio().validar("SELECT * FROM usuario"));
        assertThrows(IllegalArgumentException.class,
                () -> servicio().validar("SELECT * FROM ia_auditoria"));
    }

    @Test
    void rechazaSentenciasQueNoSonSelect() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio().validar("EXPLAIN SELECT * FROM proyecto"));
    }
}
