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
 * Cliente de la API de Gemini (Google AI Studio). Es el proveedor por defecto porque
 * es el unico que ofrece chat y embeddings dentro del tier gratuito: OpenRouter no
 * tiene ningun modelo de embeddings gratuito, y el RAG los necesita.
 *
 * <p>La API no es compatible con la de OpenAI: usa {@code contents}/{@code parts} y
 * {@code systemInstruction} en lugar de {@code messages}, y devuelve
 * {@code candidates[0].content.parts}. Por eso es un cliente aparte y no una
 * reutilizacion del de OpenRouter.
 *
 * <p>La clave viaja en la cabecera {@code x-goog-api-key} y solo en el servidor:
 * HU-09 exige que nunca salga del backend.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ia.modelo.proveedor", havingValue = "gemini", matchIfMissing = true)
public class GeminiClient implements ModeloCliente {

    /** Rol que Gemini exige en cada turno de la conversacion. */
    private static final String ROL_USUARIO = "user";

    /**
     * Espera del primer reintento. Medido contra este proveedor: un corte de conexion
     * puede durar varios segundos, asi que una espera de milisegundos se agota antes de
     * que la red vuelva. La siguiente espera multiplica por el intento, con un tope.
     */
    private static final long pausaBase = 1200L;
    private static final long pausaTope = 6000L;

    private final IaProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean disponible() {
        String key = clave();
        return key != null && !key.isBlank();
    }

    @Override
    public String variableClave() {
        return properties.getModelo().getVariableClave();
    }

    @Override
    public String generar(String sistema, String usuario) {
        ObjectNode body = mapper.createObjectNode();
        if (sistema != null && !sistema.isBlank()) {
            body.set("systemInstruction", contenido(sistema));
        }
        ArrayNode contents = mapper.createArrayNode();
        // Un solo turno de usuario: la app ya arma el historial dentro del texto que
        // envia, y Gemini rechaza un contents que empiece por un turno de modelo.
        contents.add(contenidoRol(ROL_USUARIO, usuario));
        body.set("contents", contents);

        ObjectNode config = body.putObject("generationConfig");
        config.put("temperature", properties.getModelo().getTemperatura());
        config.put("maxOutputTokens", properties.getModelo().getMaxTokens());
        Integer pensamiento = properties.getModelo().getPresupuestoPensamiento();
        if (pensamiento != null) {
            // En Gemini 3 el control es thinkingLevel y no thinkingBudget. Se envia solo
            // si el administrador lo pidio de forma explicita, porque el valor por
            // defecto del modelo es valido y no conviene fijarlo a ciegas.
            ObjectNode thinking = config.putObject("thinkingConfig");
            thinking.put("thinkingBudget", pensamiento);
            thinking.put("includeThoughts", false);
        }

        JsonNode respuesta = llamar(modeloChat(), ":generateContent", body);
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : respuesta.path("candidates").path(0).path("content").path("parts")) {
            texto.append(parte.path("text").asText(""));
        }
        if (texto.isEmpty()) {
            // Un 200 con candidates vacio es el sintoma de dos cosas distintas y el
            // mensaje las separa para que el aviso sea accionable.
            String motivo = respuesta.path("promptFeedback").path("blockReason").asText("");
            if (!motivo.isBlank()) {
                throw new IllegalStateException("La peticion fue bloqueada por el filtro de seguridad ("
                        + motivo + "). Reformule la consulta.");
            }
            String fin = respuesta.path("candidates").path(0).path("finishReason").asText("desconocido");
            throw new IllegalStateException("El modelo no devolvio texto (finishReason=" + fin
                    + "). Suele ser que thinking consumio el maxOutputTokens de "
                    + properties.getModelo().getMaxTokens()
                    + ": subalo o baja ia.modelo.presupuesto-pensamiento.");
        }
        return texto.toString().trim();
    }

    @Override
    public float[] embedDocumento(String texto) {
        return embed(texto, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public float[] embedConsulta(String texto) {
        return embed(texto, "RETRIEVAL_QUERY");
    }

    /**
     * Indexar un documento son decenas de fragmentos, y una llamada HTTP por fragmento
     * agota el limite por minuto del tier gratuito a mitad del archivo. Por eso el RAG
     * pide los vectores en lote y este metodo los resuelve en una sola peticion.
     */
    @Override
    public float[][] embedDocumentosLote(List<String> textos) {
        if (textos.isEmpty()) {
            return new float[0][];
        }
        int lote = Math.max(1, properties.getModelo().getLoteEmbeddings());
        List<float[]> salida = new ArrayList<>();
        for (int desde = 0; desde < textos.size(); desde += lote) {
            List<String> grupo = textos.subList(desde, Math.min(desde + lote, textos.size()));
            ObjectNode body = mapper.createObjectNode();
            ArrayNode requests = body.putArray("requests");
            for (String texto : grupo) {
                ObjectNode request = requests.addObject();
                request.put("model", "models/" + modeloEmbedding());
                request.put("taskType", "RETRIEVAL_DOCUMENT");
                request.put("outputDimensionality", properties.getModelo().getDimensionesEmbedding());
                request.set("content", contenido(texto));
            }
            JsonNode respuesta = llamar(modeloEmbedding(), ":batchEmbedContents", body);
            JsonNode embeddings = respuesta.path("embeddings");
            if (embeddings.size() != grupo.size()) {
                throw new IllegalStateException("El proveedor devolvio " + embeddings.size()
                        + " embeddings de los " + grupo.size() + " solicitados");
            }
            for (JsonNode embedding : embeddings) {
                salida.add(vector(embedding.path("values")));
            }
        }
        return salida.toArray(new float[0][]);
    }

    private float[] embed(String texto, String taskType) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", "models/" + modeloEmbedding());
        body.put("taskType", taskType);
        body.put("outputDimensionality", properties.getModelo().getDimensionesEmbedding());
        body.set("content", contenido(texto));
        return vector(llamar(modeloEmbedding(), ":embedContent", body)
                .path("embedding").path("values"));
    }

    private float[] vector(JsonNode valores) {
        if (!valores.isArray() || valores.isEmpty()) {
            throw new IllegalStateException("El proveedor devolvio un embedding vacio");
        }
        float[] out = new float[valores.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) valores.get(i).asDouble();
        }
        return out;
    }

    /**
     * Bloque de texto sin rol. Es lo que llevan systemInstruction y los embeddings: alli
     * el rol es opcional y los ejemplos de la documentacion no lo incluyen, asi que se
     * omite para no mandar de mas.
     */
    private ObjectNode contenido(String texto) {
        ObjectNode node = mapper.createObjectNode();
        node.putArray("parts").addObject().put("text", texto == null ? "" : texto);
        return node;
    }

    /** Turno de conversacion. El rol si es obligatorio: Gemini lo exige en contents. */
    private ObjectNode contenidoRol(String rol, String texto) {
        ObjectNode node = contenido(texto);
        node.put("role", rol);
        return node;
    }

    /**
     * Unica salida HTTP del proveedor. El modelo viaja como parametro y no como dato de
     * la clase: chat y embeddings son modelos distintos, y usar el de chat en la URL
     * hace que la API responda 404 al pedir embeddings.
     *
     * <p>Es visible para el paquete y no privado a proposito: los tests sustituyen esta
     * llamada para comprobar lo que se envia sin gastar peticiones reales.
     */
    JsonNode llamar(String modelo, String operacion, ObjectNode body) {
        String base = properties.getModelo().getBaseUrl();
        // HU-09: bloquea cualquier egreso fuera del dominio permitido.
        DominioPermitido.exigir(properties.getModelo().getDominiosPermitidos(), base);
        String key = clave();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Falta configurar la clave del modelo (variable "
                    + variableClave() + ")");
        }
        int intentos = Math.max(1, properties.getModelo().getReintentos() + 1);
        RuntimeException ultimo = null;
        for (int intento = 1; intento <= intentos; intento++) {
            try {
                return enviar(base, key, modelo, operacion, body);
            } catch (RuntimeException e) {
                ultimo = e;
                if (intento == intentos || !reintentable(e)) {
                    break;
                }
                // El tier gratuito devuelve picos de demanda (503) y cortes por limite
                // por minuto (429) de forma regular, y la red se corta de vez en cuando.
                // Todos son transitorios: reintentar evita que el asistente falle al azar.
                esperar(Math.min(pausaBase * intento, pausaTope));
            }
        }
        throw interpretar(ultimo);
    }

    /**
     * Una sola peticion, sin politica de reintento. Es el punto mas bajo de la salida
     * HTTP: los tests sustituyen esta para ejercitar el reintento sin gastar peticiones.
     */
    JsonNode enviar(String base, String key, String modelo, String operacion, ObjectNode body) {
        String json = RestClient.builder().baseUrl(base).build()
                .post()
                .uri("/v1beta/models/{model}" + operacion, modelo)
                .header("x-goog-api-key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        JsonNode respuesta = mapper.readTree(json == null ? "{}" : json);
        if (respuesta.has("error")) {
            throw new IllegalStateException(respuesta.path("error").path("message").asText(
                    "el proveedor rechazo la peticion"));
        }
        return respuesta;
    }

    /** Solo lo que se resolve con otra peticion. Un 400 o un 404 no se reintentan. */
    private boolean reintentable(RuntimeException e) {
        if (e instanceof org.springframework.web.client.HttpStatusCodeException status) {
            int codigo = status.getStatusCode().value();
            return codigo == 429 || codigo == 500 || codigo == 502 || codigo == 503 || codigo == 504;
        }
        // Un corte de conexion o un timeout llegan envueltos en ResourceAccessException,
        // y tambien se resuelven con otro intento.
        return e instanceof org.springframework.web.client.ResourceAccessException;
    }

    private void esperar(long milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpio la llamada al modelo", e);
        }
    }

    /** Traduce el fallo del proveedor a un mensaje que diga que hacer. */
    private RuntimeException interpretar(RuntimeException e) {
        String mensaje = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof org.springframework.web.client.HttpStatusCodeException status) {
            int codigo = status.getStatusCode().value();
            String detalle = cuerpoDeError(status.getResponseBodyAsString());
            if (codigo == 429) {
                return new IllegalStateException("Se alcanzo el limite por minuto del tier "
                        + "gratuo (429) y ya se reintento. Vuelva a intentar en unos "
                        + "segundos o suba ia.modelo.reintentos.", e);
            }
            if (codigo == 503 || codigo == 500 || codigo == 502 || codigo == 504) {
                return new IllegalStateException("El proveedor del modelo no esta disponible "
                        + "ahora mismo (" + codigo + ") y se agoto el reintento: " + detalle, e);
            }
            if (codigo == 400 || codigo == 422) {
                return new IllegalStateException("El proveedor rechazo la peticion: " + detalle, e);
            }
            if (codigo == 401 || codigo == 403) {
                return new IllegalStateException("La clave del modelo no es valida o el "
                        + "proyecto no tiene acceso a este modelo (" + codigo + "): " + detalle, e);
            }
            return new IllegalStateException("El proveedor respondio " + codigo + ": " + detalle, e);
        }
        // Un fallo de red no trae mensaje util ("I/O error on POST request: null"): lo que
        // explica el problema es la causa de fondo, y sin ella el aviso no dice nada.
        return new IllegalStateException("No se pudo conectar con el proveedor del modelo: "
                + causaRaiz(e), e);
    }

    /** Encadena hasta el final el mensaje mas concreto de la excepcion. */
    private String causaRaiz(Throwable e) {
        Throwable actual = e;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        String mensaje = actual.getMessage();
        return actual.getClass().getSimpleName()
                + (mensaje == null || mensaje.isBlank() ? "" : ": " + mensaje);
    }

    /** La respuesta de error de Gemini llega en JSON anidado; se aplana al mensaje. */
    private String cuerpoDeError(String cuerpo) {
        try {
            JsonNode nodo = mapper.readTree(cuerpo == null ? "{}" : cuerpo);
            String mensaje = nodo.path("error").path("message").asText("");
            return mensaje.isBlank() ? "sin detalle" : mensaje;
        } catch (RuntimeException e) {
            return "sin detalle";
        }
    }

    /** Acepta el nombre del modelo con o sin el prefijo {@code models/}. */
    private String modeloChat() {
        return sinPrefijo(properties.getModelo().getNombre());
    }

    private String modeloEmbedding() {
        return sinPrefijo(properties.getModelo().getEmbedding());
    }

    private String sinPrefijo(String modelo) {
        String limpio = modelo == null ? "" : modelo.trim();
        return limpio.startsWith("models/") ? limpio.substring("models/".length()) : limpio;
    }

    private String clave() {
        return properties.getModelo().getApiKey();
    }
}