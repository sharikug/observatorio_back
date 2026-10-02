package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.Fragmento;
import com.laboratory.auth.observatorio.ia.api.dto.FuenteRecuperada;
import com.laboratory.auth.observatorio.ia.client.ModeloCliente;
import com.laboratory.auth.observatorio.ia.config.IaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    private final ModeloCliente modelo = mock(ModeloCliente.class);
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
        when(modelo.embedDocumentosLote(anyList()))
                .thenReturn(new float[][]{{1f, 0f}, {1f, 0f}, {1f, 0f}});

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

    /** El fallo deja ERROR y cero fragmentos: no queda nada citable. */
    @Test
    void unFalloDeEmbeddingsDejaElDocumentoEnErrorYSinFragmentos() {
        // Con embeddings en lote el fallo ocurre antes de insertar cualquier fragmento.
        // La garantia que importa es la misma: nada queda indexado a medias.
        when(modelo.embedDocumentosLote(anyList()))
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
                .thenReturn(List.of("PUBLICO", "ESTUDIANTE", "ADMINISTRADOR"));

        // Los dos roles del sistema: el estudiante ve el PUBLICO y el suyo propio; el
        // marcado solo para administradores no le corresponde.
        assertEquals(1, servicio.restringidosPara("ESTUDIANTE"));
        // Quien consulta sin sesion (EXTERNO) solo ve el PUBLICO: los otros dos quedan restringidos.
        assertEquals(2, servicio.restringidosPara("EXTERNO"));
    }

    /**
     * Cambiar de modelo de embedding deja vectores de otra dimension en la base. Sin un
     * aviso explicito, la similitud coseno entre vectores de distinta longitud da 0 y el
     * asistente responderia con fuentes ordenadas al azar: es peor que fallar.
     */
    @Test
    void avisaQueHayQueReindexarSiLosVectoresCambiaronDeDimension() throws Exception {
        when(modelo.embedConsulta(anyString())).thenReturn(new float[]{1f, 0f});
        ResultSet fila = mock(ResultSet.class);
        when(fila.getString("roles_permitidos")).thenReturn("PUBLICO");
        when(fila.getString("embedding")).thenReturn("1.0,2.0,3.0,4.0");
        doAnswer(inv -> {
            ((RowCallbackHandler) inv.getArgument(1)).processRow(fila);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(RagService.DISPONIBLE));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> servicio.recuperar("cuantos proyectos hay", "EXTERNO"));

        assertTrue(error.getMessage().contains("Vuelva a cargar los documentos"),
                "el aviso debe decir que hacer: " + error.getMessage());
    }

    /** Con el corpus al dia la recuperacion funciona y ordena por similitud. */
    @Test
    void recuperaYOrdenaCuandoLasDimensionesCoinciden() throws Exception {
        when(modelo.embedConsulta(anyString())).thenReturn(new float[]{1f, 0f});
        ResultSet cercano = mock(ResultSet.class);
        when(cercano.getString("roles_permitidos")).thenReturn("PUBLICO");
        when(cercano.getString("embedding")).thenReturn("1.0,0.1");
        when(cercano.getString("id_documento")).thenReturn("doc-1");
        when(cercano.getString("nombre")).thenReturn("informe.pdf");
        when(cercano.getString("referencia")).thenReturn("pagina 1");
        when(cercano.getString("contenido")).thenReturn("contenido cercano");
        ResultSet lejano = mock(ResultSet.class);
        when(lejano.getString("roles_permitidos")).thenReturn("PUBLICO");
        when(lejano.getString("embedding")).thenReturn("0.0,1.0");
        when(lejano.getString("id_documento")).thenReturn("doc-2");
        when(lejano.getString("nombre")).thenReturn("plan.pdf");
        when(lejano.getString("referencia")).thenReturn("pagina 1");
        when(lejano.getString("contenido")).thenReturn("contenido lejano");
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(cercano);
            h.processRow(lejano);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(RagService.DISPONIBLE));

        List<FuenteRecuperada> fuentes = servicio.recuperar("cuantos proyectos hay", "EXTERNO");

        assertEquals(2, fuentes.size());
        assertEquals("doc-1", fuentes.get(0).idDocumento(),
                "el fragmento mas similar va primero");
    }

    /**
     * Borrar una fuente tiene que llevarse tambien sus fragmentos, y en ese orden: no
     * hay clave foranea entre las dos tablas, asi que borrar el documento primero
     * dejaria vectores huerfanos que la recuperacion ya no ve pero que siguen contando
     * como incompatibles en el aviso de reindexado.
     */
    @Test
    void alEliminarUnDocumentoSeBorraTambienSusFragmentosYEnEseOrden() {
        assertTrue(servicio.eliminar("doc-1"), "un documento existente se elimina");

        List<String> Deletes = llamadas.stream().map(c -> (String) c[0])
                .filter(s -> s.startsWith("DELETE")).toList();
        assertEquals(2, Deletes.size(), "se borran fragmentos y documento: " + Deletes);
        assertTrue(Deletes.get(0).contains("ia_fragmento"),
                "los fragmentos van primero: " + Deletes);
        assertTrue(Deletes.get(1).contains("ia_documento"),
                "el documento va al final: " + Deletes);
        assertEquals("doc-1", ((Object[]) llamadas.stream()
                .filter(c -> ((String) c[0]).startsWith("DELETE FROM ia_documento"))
                .findFirst().orElseThrow())[1]);
    }

    /** Un id que no existe se informa como tal, no como un borrado exitoso. */
    @Test
    void eliminarUnDocumentoInexistenteNoDiceQueSeBorro() {
        org.mockito.Mockito.doReturn(0).when(jdbc)
                .update(eq("DELETE FROM ia_documento WHERE id_documento = ?"), any(Object[].class));

        assertFalse(servicio.eliminar("no-existe"));
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
