package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.ConsultaResultado;
import com.laboratory.auth.observatorio.ia.client.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HU-01/02/03/11: traduccion de lenguaje natural a SQL de solo lectura sobre el
 * esquema del Observatorio. Nunca se permite escritura ni acceso a tablas fuera
 * de la lista blanca.
 */
@Service
@RequiredArgsConstructor
public class TextToSqlService {

    private static final int MAX_FILAS = 200;

    private static final Set<String> PROHIBIDAS = Set.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "create",
            "grant", "revoke", "merge", "call", "copy", "vacuum", "comment",
            "execute", "into", "set", "do", "commit", "rollback");

    private static final Set<String> EXCLUIDAS = Set.of("usuario", "reporte_generado");

    private static final Pattern REFERENCIA_TABLA =
            Pattern.compile("\\b(?:from|join)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CTE =
            Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s+as\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMITE = Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final GeminiClient gemini;

    private volatile String esquemaCache;

    public String descripcionEsquema() {
        if (esquemaCache == null) {
            esquemaCache = construirEsquema();
        }
        return esquemaCache;
    }

    public Set<String> tablasPermitidas() {
        Set<String> tablas = new LinkedHashSet<>();
        jdbc.query("SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                rs -> {
                    tablas.add(rs.getString(1));
                });
        tablas.removeIf(this::excluida);
        return tablas;
    }

    public String generarSql(String pregunta) {
        String sistema = """
                Eres un generador de SQL para PostgreSQL. A partir del esquema entregado,
                escribe UNA sola consulta de solo lectura (SELECT o WITH) que responda la
                pregunta en espanol. Reglas estrictas:
                - Solo lectura. Prohibido INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE.
                - Usa unicamente las tablas del esquema.
                - Devuelve exclusivamente el SQL, sin explicaciones ni markdown.
                - Si la pregunta no se puede responder con el esquema, responde: NO_DISPONIBLE
                Esquema:
                """ + descripcionEsquema();
        return gemini.generar(sistema, pregunta).trim();
    }

    public ConsultaResultado consultar(String pregunta) {
        return ejecutar(generarSql(pregunta));
    }

    /** Ejecuta un SQL ya generado aplicando validacion y limite de filas. */
    public ConsultaResultado ejecutar(String sqlGenerado) {
        String sql = limpiar(sqlGenerado);
        if (sql.equalsIgnoreCase("NO_DISPONIBLE") || sql.isBlank()) {
            return new ConsultaResultado("", List.of(), List.of());
        }
        validar(sql);
        sql = conLimite(sql);
        List<Map<String, Object>> filas = jdbc.queryForList(sql);
        List<String> columnas = filas.isEmpty() ? List.of() : new ArrayList<>(filas.get(0).keySet());
        List<List<Object>> datos = new ArrayList<>();
        for (int i = 0; i < filas.size() && i < MAX_FILAS; i++) {
            datos.add(new ArrayList<>(filas.get(i).values()));
        }
        return new ConsultaResultado(sql, columnas, datos);
    }

    /** HU-03/HU-11: valida sintacticamente y bloquea cualquier operacion de escritura. */
    public void validar(String sql) {
        String normalizado = sql.trim().toLowerCase(Locale.ROOT);
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException("La consulta generada esta vacia");
        }
        if (!normalizado.startsWith("select") && !normalizado.startsWith("with")) {
            throw new IllegalArgumentException("Solo se permiten consultas de lectura (SELECT/WITH)");
        }
        if (normalizado.contains(";")) {
            throw new IllegalArgumentException("No se permiten multiples sentencias");
        }
        for (String prohibida : PROHIBIDAS) {
            if (Pattern.compile("\\b" + prohibida + "\\b").matcher(normalizado).find()) {
                throw new IllegalArgumentException("Operacion no permitida: " + prohibida);
            }
        }
        Set<String> permitidas = tablasPermitidas();
        Set<String> ctes = new LinkedHashSet<>();
        Matcher c = CTE.matcher(sql);
        while (c.find()) {
            ctes.add(c.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher m = REFERENCIA_TABLA.matcher(sql);
        while (m.find()) {
            String tabla = m.group(1).toLowerCase(Locale.ROOT);
            if (ctes.contains(tabla)) {
                continue;
            }
            if (excluida(tabla) || !permitidas.contains(tabla)) {
                throw new IllegalArgumentException("Tabla fuera de la lista blanca: " + tabla);
            }
        }
    }

    private String limpiar(String sql) {
        String limpio = sql.trim();
        if (limpio.startsWith("```")) {
            limpio = limpio.replaceAll("(?is)^```[a-z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        while (limpio.endsWith(";")) {
            limpio = limpio.substring(0, limpio.length() - 1).trim();
        }
        return limpio;
    }

    public String conLimite(String sql) {
        return LIMITE.matcher(sql).find() ? sql : sql + " LIMIT " + MAX_FILAS;
    }

    private boolean excluida(String tabla) {
        return EXCLUIDAS.contains(tabla) || tabla.startsWith("ia_");
    }

    private String construirEsquema() {
        StringBuilder sb = new StringBuilder();
        Set<String> tablas = tablasPermitidas();
        for (String tabla : tablas) {
            sb.append("Tabla ").append(tabla).append(": ");
            List<String> columnas = new ArrayList<>();
            jdbc.query("SELECT column_name, data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position",
                    rs -> {
                        columnas.add(rs.getString("column_name") + " " + rs.getString("data_type"));
                    }, tabla);
            sb.append(String.join(", ", columnas)).append('\n');
        }
        return sb.toString();
    }
}
