package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.Fragmento;
import com.laboratory.auth.observatorio.ia.api.dto.FuenteRecuperada;
import com.laboratory.auth.observatorio.ia.client.ModeloCliente;
import com.laboratory.auth.observatorio.ia.config.IaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * HU-04/05/06/08: almacena embeddings y recupera fragmentos autorizados.
 * El filtrado por permisos se aplica ANTES de generar la respuesta (HU-08).
 *
 * ponytail: similitud coseno en Java sobre todo el corpus. Suficiente para el
 * volumen institucional; migrar a pgvector si supera unas decenas de miles de chunks.
 */
@Service
@RequiredArgsConstructor
public class RagService {

    /** HU-06: estados del ciclo de indexacion que el administrador puede ver. */
    public static final String PENDIENTE = "PENDIENTE";
    public static final String DISPONIBLE = "DISPONIBLE";
    public static final String ERROR = "ERROR";

    private final JdbcTemplate jdbc;
    private final ModeloCliente modelo;
    private final IaProperties properties;

    /**
     * HU-06: indexa un documento y solo lo declara disponible cuando esta completo.
     *
     * <p>El estado se escribe al final, no al principio. Si una llamada de embeddings
     * falla en el fragmento 40 de 100, un documento marcado como disponible desde el
     * inicio quedaria con la mitad del contenido y el asistente lo citaria como si
     * estuviera entero: por eso el fallo borra los fragmentos y deja ERROR.
     *
     * <p>Indexar es sincrono, asi que PROCESANDO/INDEXANDO no se usan: si manana se
     * mueve a un pipeline asincrono, esos estados ya estan previstos aqui.
     */
    public String indexar(String nombre, String tipo, String fuente, String usuario, List<String> roles,
                          List<Fragmento> fragmentos) {
        String id = UUID.randomUUID().toString();
        String rolesCsv = normalizarRoles(roles);
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO ia_documento "
                        + "(id_documento, nombre, tipo, fuente, autorizado, roles_permitidos, estado, fecha_carga, usuario) "
                        + "VALUES (?, ?, ?, ?, TRUE, ?, ?, ?, ?)",
                id, nombre, tipo, fuente, rolesCsv, PENDIENTE, ahora, usuario);
        try {
            // Un lote por documento: a una llamada HTTP por fragmento, el tier gratuito
            // del proveedor se agota antes de terminar un archivo de cien fragmentos.
            List<String> contenidos = fragmentos.stream().map(Fragmento::contenido).toList();
            float[][] vectores = modelo.embedDocumentosLote(contenidos);
            if (vectores.length != fragmentos.size()) {
                throw new IllegalStateException("El proveedor devolvio " + vectores.length
                        + " embeddings para " + fragmentos.size() + " fragmentos");
            }
            int orden = 0;
            for (int i = 0; i < fragmentos.size(); i++) {
                Fragmento f = fragmentos.get(i);
                jdbc.update("INSERT INTO ia_fragmento "
                                + "(id_documento, orden, contenido, referencia, embedding, roles_permitidos) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        id, orden++, f.contenido(), f.referencia(), serializar(vectores[i]), rolesCsv);
            }
        } catch (RuntimeException e) {
            jdbc.update("DELETE FROM ia_fragmento WHERE id_documento = ?", id);
            marcar(id, ERROR, "Fallo la generacion de embeddings: " + e.getMessage());
            throw e;
        }
        marcar(id, DISPONIBLE, null);
        return id;
    }

    private void marcar(String id, String estado, String error) {
        jdbc.update("UPDATE ia_documento SET estado = ?, detalle_error = ? WHERE id_documento = ?",
                estado, error, id);
    }

    /**
     * Recupera fragmentos que el rol puede ver. Solo se consultan documentos DISPONIBLE:
     * uno a medio indexar o con error no puede citarse como si estuviera completo.
     *
     * <p>Los fragmentos de documentos restringidos no llegan a puntuarse: el filtro va en
     * la propia consulta, antes de leer el contenido (HU-08).
     */
    public List<FuenteRecuperada> recuperar(String consulta, String rol) {
        // El rol del embedding importa: el modelo trata distinto el texto que se indexa
        // y el que busca, y usar el equivocado degrada la recuperacion en silencio.
        float[] objetivo = modelo.embedConsulta(consulta);
        List<FuenteRecuperada> puntuadas = new ArrayList<>();
        // Vectores de otra dimension: guardarlos sin avisar daria citas ordenadas al
        // azar. Se cuentan para poder distinguir "no hay nada" de "hay que reindexar".
        int[] incompatibles = new int[1];
        jdbc.query("SELECT f.contenido, f.referencia, f.embedding, d.roles_permitidos, "
                        + "d.id_documento, d.nombre "
                        + "FROM ia_fragmento f JOIN ia_documento d ON d.id_documento = f.id_documento "
                        + "WHERE d.autorizado = TRUE AND d.estado = ?",
                rs -> {
                    // El permiso se resuelve antes de leer y puntuar el contenido: un
                    // fragmento restringido no compite ni llega a existir para este rol.
                    if (!puedeVer(rs.getString("roles_permitidos"), rol)) {
                        return;
                    }
                    float[] vector = deserializar(rs.getString("embedding"));
                    if (vector.length != objetivo.length) {
                        incompatibles[0]++;
                        return;
                    }
                    double score = coseno(objetivo, vector);
                    puntuadas.add(new FuenteRecuperada(
                            rs.getString("id_documento"), rs.getString("nombre"),
                            rs.getString("referencia"), rs.getString("contenido"), score));
                }, DISPONIBLE);
        if (puntuadas.isEmpty() && incompatibles[0] > 0) {
            // Cambiar de modelo de embedding deja vectores de otra dimension en la base.
            // Fallar con un mensaje que diga que reindexar es mejor que devolver una
            // respuesta sin fuentes: el asistente no puede citar lo que no encuentra.
            throw new IllegalStateException("Los " + incompatibles[0] + " fragmentos almacenados "
                    + "tienen una dimension distinta a la del modelo configurado ("
                    + objetivo.length + "). Vuelva a cargar los documentos para reindexarlos "
                    + "con el modelo de embeddings actual.");
        }
        puntuadas.sort((a, b) -> Double.compare(b.score(), a.score()));
        int max = properties.getModelo().getMaxFragmentos();
        return puntuadas.size() > max ? puntuadas.subList(0, max) : puntuadas;
    }

    /**
     * HU-08: documentos DISPONIBLE que existen pero cuyo contenido este rol no puede leer.
     * Solo se cuenta: ni el nombre ni el contenido restringido se revelan, para que el
     * aviso sea "le falta informacion que no puede ver" y no una guia de que hay.
     */
    public int restringidosPara(String rol) {
        List<String> visibles = jdbc.query("SELECT roles_permitidos FROM ia_documento "
                        + "WHERE autorizado = TRUE AND estado = ?",
                (rs, i) -> rs.getString(1), DISPONIBLE);
        return (int) visibles.stream().filter(r -> !puedeVer(r, rol)).count();
    }

    /**
     * Elimina un documento junto con todos sus fragmentos. Devuelve false si el
     * documento no existia, para que el endpoint pueda distinguir "no habia nada" de
     * "no se pudo borrar".
     *
     * <p>Los fragmentos van primero y en la misma transaccion porque entre las dos
     * tablas no hay clave foranea: al borrar solo el documento quedarian vectores
     * huerfanos, invisibles para la recuperacion pero presentes en la base, y ademas
     * contarian como vectores incompatibles en el aviso de "reindexe el documento".
     * Es una operacion definitiva: el texto y los embeddings no se pueden recuperar.
     */
    @Transactional
    public boolean eliminar(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Falta el identificador del documento");
        }
        jdbc.update("DELETE FROM ia_fragmento WHERE id_documento = ?", id);
        return jdbc.update("DELETE FROM ia_documento WHERE id_documento = ?", id) > 0;
    }

    public List<java.util.Map<String, Object>> documentos() {
        return jdbc.queryForList("SELECT id_documento, nombre, tipo, fuente, roles_permitidos, "
                + "estado, usuario, detalle_error, fecha_carga FROM ia_documento ORDER BY fecha_carga DESC");
    }

    /** HU-06: estado de indexacion de un documento. */
    public Map<String, Object> estado(String id) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id_documento, nombre, tipo, estado, usuario, detalle_error, fecha_carga, "
                        + "(SELECT COUNT(*) FROM ia_fragmento f WHERE f.id_documento = ia_documento.id_documento) "
                        + "AS fragmentos FROM ia_documento WHERE id_documento = ?", id);
        return filas.isEmpty()
                ? Map.of("estado", "NO_ENCONTRADO", "mensaje", "El documento no existe")
                : filas.get(0);
    }

    public boolean puedeVer(String rolesCsv, String rol) {
        String roles = rolesCsv == null ? "PUBLICO" : rolesCsv.toUpperCase(Locale.ROOT);
        if (roles.isBlank()) {
            roles = "PUBLICO";
        }
        String actual = rol == null ? "EXTERNO" : rol.toUpperCase(Locale.ROOT);
        if (actual.contains("ADMIN") || actual.contains("GESTOR") || actual.contains("ANALISTA")) {
            return true;
        }
        return roles.contains("PUBLICO")
                || roles.contains(actual)
                || roles.contains("EXTERNO");
    }

    private String normalizarRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "PUBLICO";
        }
        return String.join(",", roles.stream().map(r -> r.toUpperCase(Locale.ROOT)).toList());
    }

    private String serializar(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private float[] deserializar(String valor) {
        if (valor == null || valor.isBlank()) {
            return new float[0];
        }
        String[] partes = valor.split(",");
        float[] vector = new float[partes.length];
        for (int i = 0; i < partes.length; i++) {
            vector[i] = Float.parseFloat(partes[i]);
        }
        return vector;
    }

    private double coseno(float[] a, float[] b) {
        if (a.length == 0 || a.length != b.length) {
            return 0;
        }
        double producto = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            producto += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return producto / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
