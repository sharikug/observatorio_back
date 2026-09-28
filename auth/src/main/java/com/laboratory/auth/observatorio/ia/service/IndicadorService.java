package com.laboratory.auth.observatorio.ia.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HU-04: diccionario de indicadores versionado, fuente autorizada del asistente.
 * Se carga desde recursos/ia/indicadores.json a la tabla ia_indicador.
 */
@Service
@RequiredArgsConstructor
public class IndicadorService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void cargar() {
        try (InputStream in = new ClassPathResource("ia/indicadores.json").getInputStream()) {
            JsonNode raiz = mapper.readTree(in);
            for (JsonNode n : raiz) {
                jdbc.update("INSERT INTO ia_indicador "
                                + "(id_indicador, nombre, definicion, formula, unidad, fuente, enlace, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON CONFLICT (id_indicador) DO UPDATE SET "
                                + "nombre = EXCLUDED.nombre, definicion = EXCLUDED.definicion, "
                                + "formula = EXCLUDED.formula, unidad = EXCLUDED.unidad, "
                                + "fuente = EXCLUDED.fuente, enlace = EXCLUDED.enlace, "
                                + "version = EXCLUDED.version",
                        n.path("id").asText(), n.path("nombre").asText(),
                        n.path("definicion").asText(), n.path("formula").asText(),
                        n.path("unidad").asText(), n.path("fuente").asText(),
                        n.path("enlace").asText(), n.path("version").asInt());
            }
        } catch (Exception e) {
            // No debe impedir el arranque; el asistente reportara el diccionario vacio.
            System.err.println("No se pudo cargar el diccionario de indicadores: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listar() {
        return jdbc.queryForList("SELECT id_indicador, nombre, definicion, formula, unidad, fuente, "
                + "enlace, version FROM ia_indicador ORDER BY id_indicador");
    }

    /** Busca indicadores relevantes por coincidencia de tokens del nombre. */
    public List<ObjectNode> buscar(String consulta) {
        String q = consulta == null ? "" : consulta.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> todos = listar();
        List<ObjectNode> out = new ArrayList<>();
        for (Map<String, Object> ind : todos) {
            String nombre = String.valueOf(ind.get("nombre")).toLowerCase(Locale.ROOT);
            boolean coincide = q.isBlank();
            for (String token : nombre.split("\\s+")) {
                if (token.length() > 3 && q.contains(token)) {
                    coincide = true;
                }
            }
            if (coincide) {
                ObjectNode node = mapper.createObjectNode();
                Map<String, Object> ordenado = new LinkedHashMap<>(ind);
                ordenado.forEach((k, v) -> node.put(k, v == null ? null : String.valueOf(v)));
                out.add(node);
            }
        }
        return out;
    }
}
