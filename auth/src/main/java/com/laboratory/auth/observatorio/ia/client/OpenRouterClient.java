package com.laboratory.auth.observatorio.ia.client;

import com.laboratory.auth.observatorio.ia.config.IaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Cliente de OpenRouter. Alternativa al proveedor por defecto: solo se registra como
 * bean si {@code ia.modelo.proveedor=openrouter}, de modo que nunca queden dos
 * implementaciones de {@link ModeloCliente} compitiendo por inyeccion.
 *
 * <p>Nota para quien configure esto: OpenRouter no ofrece ningun modelo de
 * embeddings gratuito, asi que este proveedor no sirve para el tier sin costo. La API
 * es compatible con la de OpenAI, asi que el mismo cliente sirve para
 * /chat/completions y /embeddings sin dependencias adicionales.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ia.modelo.proveedor", havingValue = "openrouter")
public class OpenRouterClient implements ModeloCliente {

    private final IaProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean disponible() {
        String key = properties.getModelo().getApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String variableClave() {
        return properties.getModelo().getVariableClave();
    }

    @Override
    public String generar(String sistema, String usuario) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.getModelo().getNombre());
        ArrayNode mensajes = mapper.createArrayNode();
        mensajes.add(mensaje("system", sistema));
        mensajes.add(mensaje("user", usuario));
        body.set("messages", mensajes);
        body.put("temperature", properties.getModelo().getTemperatura());
        body.put("max_tokens", properties.getModelo().getMaxTokens());

        JsonNode respuesta = llamar("/chat/completions", body);
        return respuesta.path("choices").path(0).path("message").path("content").asText("").trim();
    }

    /**
     * OpenRouter expone un solo modelo de embeddings, asi que la distincion entre
     * documento y consulta no aplica: ambos roles van al mismo endpoint.
     */
    @Override
    public float[] embedDocumento(String texto) {
        return embed(texto);
    }

    @Override
    public float[] embedConsulta(String texto) {
        return embed(texto);
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
        DominioPermitido.exigir(properties.getModelo().getDominiosPermitidos(), base);
        String key = properties.getModelo().getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Falta configurar la clave del modelo (variable "
                    + variableClave() + ")");
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
