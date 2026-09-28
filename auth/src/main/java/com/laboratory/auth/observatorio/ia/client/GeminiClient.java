package com.laboratory.auth.observatorio.ia.client;

import com.laboratory.auth.observatorio.ia.config.IaProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Cliente del proveedor de modelo (Gemini). Unico punto de egreso del modulo:
 * HU-09 exige que solo exista trafico hacia los dominios permitidos del proveedor.
 */
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final IaProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean disponible() {
        String key = properties.getGemini().getApiKey();
        return key != null && !key.isBlank();
    }

    public String generar(String sistema, String usuario) {
        ObjectNode body = mapper.createObjectNode();
        body.set("systemInstruction", parts(sistema));
        ArrayNode contents = mapper.createArrayNode();
        ObjectNode userContent = mapper.createObjectNode();
        userContent.put("role", "user");
        userContent.set("parts", parts(usuario));
        contents.add(userContent);
        body.set("contents", contents);
        ObjectNode config = mapper.createObjectNode();
        config.put("temperature", 0.2);
        config.put("maxOutputTokens", 4096);
        body.set("generationConfig", config);

        JsonNode respuesta = llamar(
                "/models/" + properties.getGemini().getModelo() + ":generateContent", body);
        StringBuilder texto = new StringBuilder();
        JsonNode candidatos = respuesta.path("candidates");
        if (candidatos.isArray() && !candidatos.isEmpty()) {
            for (JsonNode part : candidatos.get(0).path("content").path("parts")) {
                texto.append(part.path("text").asText());
            }
        }
        return texto.toString().trim();
    }

    public float[] embed(String texto) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", "models/" + properties.getGemini().getModeloEmbeddings());
        ObjectNode content = mapper.createObjectNode();
        content.set("parts", parts(texto));
        body.set("content", content);

        JsonNode respuesta = llamar(
                "/models/" + properties.getGemini().getModeloEmbeddings() + ":embedContent", body);
        JsonNode valores = respuesta.path("embedding").path("values");
        List<Float> floats = new ArrayList<>();
        if (valores.isArray()) {
            for (JsonNode v : valores) {
                floats.add((float) v.asDouble());
            }
        }
        float[] out = new float[floats.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = floats.get(i);
        }
        return out;
    }

    private JsonNode llamar(String ruta, ObjectNode body) {
        String base = properties.getGemini().getBaseUrl();
        // HU-09: bloquea cualquier egreso fuera del dominio permitido.
        boolean permitido = properties.getGemini().getDominiosPermitidos().stream()
                .anyMatch(base::contains);
        if (!permitido) {
            throw new IllegalStateException(
                    "Destino de egreso no permitido por el alcance cerrado: " + base);
        }
        String key = properties.getGemini().getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar ia.gemini.api-key (variable GEMINI_API_KEY)");
        }
        try {
            String json = RestClient.builder().baseUrl(base).build()
                    .post()
                    .uri(uriBuilder -> uriBuilder.path(ruta).queryParam("key", key).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(json == null ? "{}" : json);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Error al invocar el modelo: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Respuesta invalida del modelo: " + e.getMessage(), e);
        }
    }

    private ObjectNode parts(String texto) {
        ObjectNode wrapper = mapper.createObjectNode();
        ArrayNode array = mapper.createArrayNode();
        ObjectNode part = mapper.createObjectNode();
        part.put("text", texto == null ? "" : texto);
        array.add(part);
        wrapper.set("parts", array);
        return wrapper;
    }
}
