package com.laboratory.auth.observatorio.ia.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-08: la memoria conversacional es contexto, no permiso. Estas pruebas fijan
 * esa frontera para que ningun cambio futuro la abra por descuido.
 */
class ConversacionServiceTest {

    private static final String ID = "c1";

    @Test
    void unaConversacionAjenaNoSeLeeNiSeBorra() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // La consulta de pertenencia filtra por email: cero filas = conversacion ajena.
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);

        ConversacionService servicio = new ConversacionService(jdbc);

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> servicio.mensajes("otro@ucundinamarca.edu.co", ID)).getStatusCode());
        assertThrows(ResponseStatusException.class,
                () -> servicio.borrar("otro@ucundinamarca.edu.co", ID));
    }

    @Test
    void elHistorialSeMarcaComoContextoNoVerificado() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq(ID), eq(12)))
                .thenReturn(List.of("Asistente: 49 grupos", "Usuario: y cuantos investigadores?"));

        String contexto = new ConversacionService(jdbc).contexto("u@ucundinamarca.edu.co", ID);

        assertTrue(contexto.contains("no la tomes como dato vigente"));
        assertTrue(contexto.contains("49 grupos"));
        // La consulta llega de la mas reciente a la mas antigua; el prompt, al reves,
        // para que el modelo lea la conversacion en orden cronologico.
        assertTrue(contexto.indexOf("cuantos investigadores") < contexto.indexOf("49 grupos"),
                contexto);
    }

    @Test
    void sinConversacionNoSeGuardaNiSePideHistorial() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConversacionService servicio = new ConversacionService(jdbc);

        assertEquals("", servicio.contexto("u@ucundinamarca.edu.co", null));
        servicio.registrarMensaje(null, "user", "hola", List.of(), null);
        verifyNoInteractions(jdbc);
    }

    /**
     * El titulo de la conversacion se actualiza en el mismo turno que guarda el mensaje.
     * La sentencia debe tener tantos marcadores como argumentos: si no, PostgreSQL la
     * rechaza y se pierde el turno completo del chat.
     */
    @Test
    void elTituloSeActualizaSinRomperElTurno() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Object[]> enviados = new ArrayList<>();
        doAnswer(inv -> {
            enviados.add(inv.getArguments());
            return 1;
        }).when(jdbc).update(anyString(), any(Object[].class));

        new ConversacionService(jdbc).registrarMensaje(ID, "user", "Cuantos grupos hay?", List.of(), true);

        assertEquals(2, enviados.size(), "debe guardar el mensaje y actualizar la conversacion");
        for (Object[] llamada : enviados) {
            String sql = (String) llamada[0];
            long marcadores = sql.chars().filter(c -> c == '?').count();
            assertEquals(llamada.length - 1, marcadores,
                    "marcadores y argumentos no coinciden en: " + sql);
        }
        // El rol se compara contra un parametro, no contra una columna "rol" que no existe
        // en ia_conversacion: esa columna era lo que hacia fallar la actualizacion.
        Object[] actualizacion = enviados.get(1);
        assertFalse(((String) actualizacion[0]).contains("WHEN rol"),
                "ia_conversacion no tiene columna rol: " + actualizacion[0]);
        assertEquals("user", actualizacion[2]);
        assertEquals("Cuantos grupos hay?", actualizacion[3], "el titulo sale del primer mensaje");
        assertEquals(ID, actualizacion[4]);
    }
}
