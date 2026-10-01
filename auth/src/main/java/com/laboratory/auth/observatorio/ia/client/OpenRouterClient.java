package com.laboratory.auth.observatorio.ia.client;

import com.laboratory.auth.observatorio.ia.config.IaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Cliente de OpenRouter. Unico punto de egreso del modulo: HU-09 exige que solo
 * exista trafico hacia los dominios permitidos del proveedor, nunca a internet.
 *
 * <p>La API de OpenRouter es compatible con la de OpenAI, asi que el mismo cliente
 * sirve para /chat/completions y /embeddings sin dependencias adicionales.
 */
@Component
@RequiredArgsConstructor
public class OpenRouterClient {

    private final IaProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean disponible() {
        String key = properties.getModelo().getApiKey();
        return key != null && !key.isBlank();
    }

    public String generar(String sistema, String usuario) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.getModelo().getNombre());
        ArrayNode mensajes = mapper.createArrayNode();
        mensajes.add(mensaje("system", sistema));
        mensajes.add(mensaje("user", usuario));
        body.set("messages", mensajes);
        body.put("temperature", 0.2);
        body.put("max_tokens", 4096);

        JsonNode respuesta = llamar("/chat/completions", body);
        return respuesta.path("choices").path(0).path("message").path("content").asText("").trim();
    }

    public float[] embed(String texto) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.getModelo().getEmbedding());
        body.put("input", texto == null ? "" : texto);
        List<Float> floats = new ArrayList<>();
        for (JsonNode valor : llamar("/embeddings", body).path("data").path(0).path("embedding")) {
            floats.add((float) valor.asDouble());
        }
        float[] out = new float[floats.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = floats.get(i);
        }
        return out;
    }

    private ObjectNode mensaje(String rol, String texto) {
        ObjectNode nodo = mapper.createObjectNode();
        nodo.put("role", rol);
        nodo.put("content", texto == null ? "" : texto);
        return nodo;
    }

    private JsonNode llamar(String ruta, ObjectNode body) {
        String base = properties.getModelo().getBaseUrl();
        // HU-09: bloquea cualquier egreso fuera del dominio permitido.
        boolean permitido = properties.getModelo().getDominiosPermitidos().stream()
                .anyMatch(base::contains);
        if (!permitido) {
            throw new IllegalStateException(
                    "Destino de egreso no permitido por el alcance cerrado: " + base);
        }
        String key = properties.getModelo().getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar ia.modelo.api-key (variable OPENROUTER_API_KEY)");
        }
        try {
            String json = RestClient.builder().baseUrl(base).build()
                    .post()
                    .uri(ruta)
                    .header("Authorization", "Bearer " + key)
                    .header("HTTP-Referer", "https://observatorio.ucundinamarca.edu.co")
                    .header("X-Title", "Observatorio IA")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            JsonNode respuesta = mapper.readTree(json == null ? "{}" : json);
            // El error del proveedor llega en HTTP 4xx/5xx; RestClient ya lanza, pero
            // OpenRouter tambien responde 200 con {"error": ...} en algunos bordes.
            if (respuesta.has("error")) {
                throw new IllegalStateException(
                        "El proveedor del modelo rechazo la peticion: "
                                + respuesta.path("error").path("message").asText("sin detalle"));
            }
            return respuesta;
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Error al invocar el modelo: " + e.getMessage(), e);
        }
    }
}
