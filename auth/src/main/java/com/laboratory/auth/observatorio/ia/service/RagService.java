package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.Fragmento;
import com.laboratory.auth.observatorio.ia.api.dto.FuenteRecuperada;
import com.laboratory.auth.observatorio.ia.client.GeminiClient;
import com.laboratory.auth.observatorio.ia.config.IaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private final JdbcTemplate jdbc;
    private final GeminiClient gemini;
    private final IaProperties properties;

    public String indexar(String nombre, String tipo, String fuente, List<String> roles,
                          List<Fragmento> fragmentos) {
        String id = UUID.randomUUID().toString();
        String rolesCsv = normalizarRoles(roles);
        jdbc.update("INSERT INTO ia_documento "
                        + "(id_documento, nombre, tipo, fuente, autorizado, roles_permitidos, estado, fecha_carga) "
                        + "VALUES (?, ?, ?, ?, TRUE, ?, ?, ?)",
                id, nombre, tipo, fuente, rolesCsv, "INDEXADO", Timestamp.valueOf(LocalDateTime.now()));
        int orden = 0;
        for (Fragmento f : fragmentos) {
            float[] vector = gemini.embed(f.contenido());
            jdbc.update("INSERT INTO ia_fragmento "
                            + "(id_documento, orden, contenido, referencia, embedding, roles_permitidos) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    id, orden++, f.contenido(), f.referencia(), serializar(vector), rolesCsv);
        }
        return id;
    }

    public List<FuenteRecuperada> recuperar(String consulta, String rol) {
        float[] objetivo = gemini.embed(consulta);
        List<FuenteRecuperada> puntuadas = new ArrayList<>();
        jdbc.query("SELECT f.contenido, f.referencia, f.embedding, f.roles_permitidos, "
                        + "d.id_documento, d.nombre "
                        + "FROM ia_fragmento f JOIN ia_documento d ON d.id_documento = f.id_documento "
                        + "WHERE d.autorizado = TRUE",
                rs -> {
                    String roles = rs.getString("roles_permitidos");
                    if (!puedeVer(roles, rol)) {
                        return;
                    }
                    float[] vector = deserializar(rs.getString("embedding"));
                    double score = coseno(objetivo, vector);
                    puntuadas.add(new FuenteRecuperada(
                            rs.getString("id_documento"), rs.getString("nombre"),
                            rs.getString("referencia"), rs.getString("contenido"), score));
                });
        puntuadas.sort((a, b) -> Double.compare(b.score(), a.score()));
        int max = properties.getGemini().getMaxFragmentos();
        return puntuadas.size() > max ? puntuadas.subList(0, max) : puntuadas;
    }

    public List<java.util.Map<String, Object>> documentos() {
        return jdbc.queryForList("SELECT id_documento, nombre, tipo, fuente, roles_permitidos, "
                + "estado, fecha_carga FROM ia_documento ORDER BY fecha_carga DESC");
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
