package com.laboratory.auth.observatorio.ia.client;

import com.laboratory.auth.observatorio.ia.config.IaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La API de Gemini no es compatible con la de OpenAI: usa contents/parts y
 * systemInstruction en lugar de messages, y devuelve candidates. Estos tests fijan
 * ese contrato porque un cambio de nombre de campo no lo detecta el compilador:
 * falla en produccion, contra la API, y solo para quien indexa un documento.
 */
class GeminiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Sustituye la red: guarda lo enviado y responde con una forma coherente con la
     * operacion, un embedding por solicitud de lote.
     *
     * <p>Sustituye enviar() y no llamar() a proposito: la politica de reintento vive en
     * llamar(), asi que sustituirla dejaria esa politica sin probar.
     */
    private final class ClienteFalso extends GeminiClient {
        private final List<ObjectNode> enviados = new ArrayList<>();
        private final List<String> operaciones = new ArrayList<>();
        private String respuesta;
        private int intentos;
        /** Si se rellena, el primer intento falla con ese codigo y los siguientes pasan. */
        private String fallarCon;
        /** Reproduce un corte de conexion, que es lo que produce un ResourceAccessException. */
        private boolean redCaida;

        ClienteFalso(IaProperties properties) {
            super(properties);
        }

        @Override
        JsonNode enviar(String base, String key, String modelo, String operacion, ObjectNode body) {
            operaciones.add(modelo + operacion);
            enviados.add(body);
            intentos++;
            if (redCaida) {
                throw new org.springframework.web.client.ResourceAccessException(
                        "I/O error on POST request: null",
                        new java.net.ConnectException("Connection refused"));
            }
            if (fallarCon != null && intentos == 1) {
                String[] partes = fallarCon.split(" ");
                throw error(Integer.parseInt(partes[0]), partes[1]);
            }
            if (respuesta != null) {
                return mapper.readTree(respuesta);
            }
            ObjectNode faux = mapper.createObjectNode();
            int cantidad = body.has("requests") ? body.path("requests").size() : 1;
            ArrayNode embeddings = faux.putArray("embeddings");
            for (int i = 0; i < cantidad; i++) {
                embeddings.addObject().putArray("values").add(1.0).add(0.0);
            }
            return faux;
        }

        /** Error HTTP con la forma que devuelve la API de Gemini. */
        private RuntimeException error(int codigo, String estado) {
            String cuerpo = "{\"error\":{\"code\":" + codigo + ",\"message\":\"" + estado
                    + "\",\"status\":\"" + estado + "\"}}";
            byte[] bytes = cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var status = org.springframework.http.HttpStatus.valueOf(codigo);
            var cabeceras = new org.springframework.http.HttpHeaders();
            var charset = java.nio.charset.StandardCharsets.UTF_8;
            // La clase concreta depende del codigo: 5xx y 4xx son subclases distintas.
            return codigo >= 500
                    ? new org.springframework.web.client.HttpServerErrorException(
                            status, estado, cabeceras, bytes, charset)
                    : org.springframework.web.client.HttpClientErrorException.create(
                            status, estado, cabeceras, bytes, charset);
        }
    }

    private IaProperties properties;

    @BeforeEach
    void configuracion() {
        properties = new IaProperties();
        properties.getModelo().setApiKey("clave-de-prueba");
        properties.getModelo().setNombre("gemini-3.5-flash-lite");
        properties.getModelo().setEmbedding("gemini-embedding-001");
        properties.getModelo().setDimensionesEmbedding(768);
    }

    @Test
    void generarMandaSystemInstructionYUnTurnoDeUsuario() {
        ClienteFalso cliente = new ClienteFalso(properties);
        cliente.respuesta = """
                {"candidates":[{"content":{"parts":[{"text":"respuesta del modelo"}]}}]}
                """;

        String texto = cliente.generar("eres el asistente", "cuantos proyectos hay");

        assertEquals("respuesta del modelo", texto);
        assertEquals("gemini-3.5-flash-lite:generateContent", cliente.operaciones.get(0));
        ObjectNode body = cliente.enviados.get(0);
        assertEquals("eres el asistente",
                body.path("systemInstruction").path("parts").path(0).path("text").asText(),
                "las reglas de sistema van en systemInstruction, no como un turno mas");
        JsonNode contents = body.path("contents");
        assertEquals(1, contents.size());
        // Gemini rechaza un contents que empiece por un turno de modelo.
        assertEquals("user", contents.path(0).path("role").asText());
        assertEquals("cuantos proyectos hay",
                contents.path(0).path("parts").path(0).path("text").asText());
    }

    /** Un 200 sin texto tiene dos causas distintas y el aviso debe distinguirlas. */
    @Test
    void unBloqueoDeSeguridadSeReportaComoTal() {
        ClienteFalso cliente = new ClienteFalso(properties);
        cliente.respuesta = """
                {"promptFeedback":{"blockReason":"SAFETY"},"candidates":[]}
                """;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cliente.generar("reglas", "pregunta"));

        assertTrue(error.getMessage().contains("seguridad"), error.getMessage());
    }

    @Test
    void unTextoVacioDiceQueSeAgotoElMaximoDeTokens() {
        ClienteFalso cliente = new ClienteFalso(properties);
        cliente.respuesta = """
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[]}}]}
                """;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cliente.generar("reglas", "pregunta"));

        assertTrue(error.getMessage().contains("maxOutputTokens"), error.getMessage());
    }

    /**
     * Documento y consulta van con taskType distinto. Usar el mismo para ambos baja la
     * calidad de la recuperacion y el fallo es silencioso: el asistente responde, pero
     * con los fragmentos equivocados.
     */
    @Test
    void documentoYConsultaUsanTaskTypeDistintos() {
        ClienteFalso cliente = new ClienteFalso(properties);
        cliente.respuesta = """
                {"embedding":{"values":[1.0,0.0]}}
                """;

        cliente.embedDocumento("texto a indexar");
        cliente.embedConsulta("pregunta del usuario");

        assertEquals("RETRIEVAL_DOCUMENT", cliente.enviados.get(0).path("taskType").asText());
        assertEquals("RETRIEVAL_QUERY", cliente.enviados.get(1).path("taskType").asText());
        assertEquals("gemini-embedding-001:embedContent", cliente.operaciones.get(0),
                "los embeddings van al modelo de embeddings: pedir embedContent sobre el "
                        + "modelo de chat hace que la API responda 404");
        assertEquals(768, cliente.enviados.get(0).path("outputDimensionality").asInt(),
                "la dimension pedida debe ser la configurada, no la del modelo por defecto");
        assertEquals("models/gemini-embedding-001",
                cliente.enviados.get(0).path("model").asText());
    }

    /**
     * Indexar son decenas de fragmentos y el tier gratis se agota por minuto: el cliente
     * tiene que trocear en lotes del tamaño configurado y devolver los vectores en el
     * orden de entrada, porque el RAG los empareja por indice con los fragmentos.
     */
    @Test
    void elLoteTroceaYConservaElOrden() {
        properties.getModelo().setLoteEmbeddings(2);
        ClienteFalso cliente = new ClienteFalso(properties);

        float[][] vectores = cliente.embedDocumentosLote(
                List.of("uno", "dos", "tres", "cuatro", "cinco"));

        assertEquals(3, cliente.operaciones.size(), "5 fragmentos en lotes de 2 son 3 peticiones");
        assertTrue(cliente.operaciones.stream()
                        .allMatch(op -> op.equals("gemini-embedding-001:batchEmbedContents")),
                "el lote va al modelo de embeddings: " + cliente.operaciones);
        assertEquals(List.of(2, 2, 1), cliente.enviados.stream()
                .map(cuerpo -> cuerpo.path("requests").size()).toList(),
                "el ultimo lote lleva el resto, no un lote lleno");
        assertEquals(5, vectores.length);
        for (float[] vector : vectores) {
            assertEquals(1f, vector[0], 0.0001f);
            assertEquals(0f, vector[1], 0.0001f);
        }
        assertEquals("RETRIEVAL_DOCUMENT",
                cliente.enviados.get(0).path("requests").path(0).path("taskType").asText());
    }

    /**
     * Un base-url con path unido a una ruta que empieza en "/" depende de como los
     * combine el cliente HTTP. Aqui se fija la URL completa que sale hacia internet:
     * equivocarse es un 404 contra la API, no un fallo de compilacion.
     */
    @Test
    void laUrlArmadaApuntaAlEndpointCorrecto() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
                url("gemini-3.5-flash", ":generateContent"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent",
                url("gemini-embedding-001", ":embedContent"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents",
                url("gemini-embedding-001", ":batchEmbedContents"));
        // El nombre puede venir con el prefijo models/ y no debe duplicarse.
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
                url("models/gemini-3.5-flash", ":generateContent"));
    }

    /** Resuelve la misma combinacion de base-url y ruta que usa el cliente real. */
    private String url(String modelo, String operacion) {
        String base = properties.getModelo().getBaseUrl();
        String limpio = modelo.startsWith("models/") ? modelo.substring("models/".length()) : modelo;
        return org.springframework.web.util.UriComponentsBuilder
                .fromUriString(base)
                .path("/v1beta/models/{model}" + operacion)
                .buildAndExpand(limpio)
                .toUriString();
    }

    /**
 * El tier gratuito devuelve picos de demanda (503) de forma regular. Si un 503 se
 * convierte en un error visible, el asistente falla al azar ante el usuario; por eso
 * se reintenta. Un 404, en cambio, no se arregla repitiendo.
     */
    @Test
    void un503SeReintentaYUn400No() {
        ClienteFalso cliente = new ClienteFalso(properties);
        properties.getModelo().setReintentos(2);

        cliente.fallarCon = "503 UNAVAILABLE";
        cliente.respuesta = """
                {"candidates":[{"content":{"parts":[{"text":"reintento ok"}]}}]}
                """;
        assertEquals("reintento ok", cliente.generar("reglas", "pregunta"));
        assertEquals(2, cliente.intentos,
                "el primer 503 debe reintentarse una vez y salir con la respuesta");

        ClienteFalso rigido = new ClienteFalso(properties);
        rigido.fallarCon = "400 INVALID_ARGUMENT";
        assertThrows(IllegalStateException.class, () -> rigido.generar("reglas", "pregunta"));
        assertEquals(1, rigido.intentos, "un 400 es un error de configuracion: no se reintenta");
    }

    /**
     * Un fallo de red llega como "I/O error on POST request: null": el mensaje no dice
     * nada. Lo util es la causa de fondo, y sin ella el aviso al admin es inservible.
     */
    @Test
    void unFalloDeRedDiceLaCausaDeFondo() {
        ClienteFalso cliente = new ClienteFalso(properties);
        properties.getModelo().setReintentos(0);
        cliente.redCaida = true;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cliente.generar("reglas", "pregunta"));

        assertTrue(error.getMessage().contains("No se pudo conectar"), error.getMessage());
        assertTrue(error.getMessage().contains("ConnectException"),
                "debe nombrar la causa real, no el wrapper: " + error.getMessage());
    }

    @Test
    void sinClaveNoSeConsideraDisponible() {
        properties.getModelo().setApiKey("  ");
        assertFalse(new GeminiClient(properties).disponible());
        assertEquals("GEMINI_API_KEY",
                new GeminiClient(properties).variableClave(),
                "el aviso al admin debe nombrar la variable configurada");
    }

    /** HU-09: un destino que solo lleva el dominio permitido en un parametro no pasa. */
    @Test
    void elDominioSeValidaContraElHostYNoContraLaCadena() {
        List<String> permitidos = List.of("generativelanguage.googleapis.com");
        // El filtro anterior comparaba con contains y esto pasaba el filtro.
        assertThrows(IllegalStateException.class,
                () -> DominioPermitido.exigir(permitidos,
                        "https://atacante.example/v1?ref=generativelanguage.googleapis.com"));
        assertThrows(IllegalStateException.class,
                () -> DominioPermitido.exigir(permitidos, "https://otro.example"));
    }
}