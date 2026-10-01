package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.Fragmento;
import com.laboratory.auth.observatorio.ia.client.OpenRouterClient;
import com.laboratory.auth.observatorio.ia.config.IaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HU-06/HU-08: un documento solo se declara disponible cuando esta completo, y un
 * fallo de embeddings no puede dejar un documento a medio indexar que el asistente
 * cite como si estuviera entero.
 */
class RagServiceTest {

    private static final List<Fragmento> TRES_FRAGMENTOS = List.of(
            new Fragmento("primer fragmento", "pagina 1"),
            new Fragmento("segundo fragmento", "pagina 1"),
            new Fragmento("tercer fragmento", "pagina 2"));

    private final List<Object[]> llamadas = new ArrayList<>();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OpenRouterClient modelo = mock(OpenRouterClient.class);
    private final RagService servicio = new RagService(jdbc, modelo, new IaProperties());

    @BeforeEach
    void registrarLlamadas() {
        doAnswer(inv -> {
            llamadas.add(inv.getArguments());
            return 1;
        }).when(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void unDocumentoCompletoQuedaDisponibleYRegistraQuienLoSubio() {
        when(modelo.embed(anyString())).thenReturn(new float[]{1f, 0f});

        String id = servicio.indexar("informe.pdf", "PDF", "DOCUMENTO", "admin@ucundinamarca.edu.co",
                List.of("PUBLICO"), TRES_FRAGMENTOS);

        String insercion = Arrays.toString(primerArgumento("INSERT INTO ia_documento"));
        assertTrue(insercion.contains(RagService.PENDIENTE),
                "se inscribe como pendiente: " + insercion);
        assertTrue(insercion.contains("admin@ucundinamarca.edu.co"),
                "HU-23: debe quedar registrado quien lo subio: " + insercion);

        assertEquals(3, count("INSERT INTO ia_fragmento"));
        assertEquals(RagService.DISPONIBLE, estadoDe(id));
        assertEquals(0, count("DELETE FROM ia_fragmento"), "un documento completo no se borra");
    }

    /** El fallo a mitad deja ERROR y cero fragmentos: no queda nada citable. */
    @Test
    void unFalloDeEmbeddingsDejaElDocumentoEnErrorYSinFragmentos() {
        when(modelo.embed(anyString()))
                .thenReturn(new float[]{1f, 0f})
                .thenThrow(new IllegalStateException("el proveedor del modelo rechazo la peticion"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> servicio.indexar("informe.pdf", "PDF", "DOCUMENTO", "admin@ucundinamarca.edu.co",
                        List.of("PUBLICO"), TRES_FRAGMENTOS));

        assertEquals("el proveedor del modelo rechazo la peticion", error.getMessage());
        assertEquals(1, count("DELETE FROM ia_fragmento"),
                "el fragmento ya insertado debe desaparecer, si no queda indexacion a medias");
        assertEquals(RagService.ERROR, estadoDe(null));
    }

    /** El aviso de informacion restringida cuenta documentos, sin revelar cuales son. */
    @Test
    void cuentaLosDocumentosQueElRolNoPuedeLeer() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(RagService.DISPONIBLE)))
                .thenReturn(List.of("PUBLICO", "DIRECTIVO", "ANALISTA"));

        // El directivo ve el PUBLICO y el suyo propio; el de ANALISTA no le corresponde.
        assertEquals(1, servicio.restringidosPara("DIRECTIVO"));
        // Quien no tiene rol solo ve el PUBLICO: los otros dos quedan restringidos.
        assertEquals(2, servicio.restringidosPara("EXTERNO"));
    }

    private int count(String sql) {
        return (int) llamadas.stream().filter(c -> ((String) c[0]).contains(sql)).count();
    }

    private Object[] primerArgumento(String sql) {
        return llamadas.stream().filter(c -> ((String) c[0]).contains(sql)).findFirst().orElse(null);
    }

    /** Estado con el que quedo el documento en la ultima llamada a indexar. */
    private String estadoDe(String id) {
        return llamadas.stream()
                .filter(c -> ((String) c[0]).startsWith("UPDATE ia_documento SET estado"))
                .map(c -> (String) c[1])
                .reduce((primero, ultimo) -> ultimo)
                .orElse(null);
    }
}
