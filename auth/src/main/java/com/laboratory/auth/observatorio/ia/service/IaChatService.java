package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.BorradorRequest;
import com.laboratory.auth.observatorio.ia.api.dto.BorradorResponse;
import com.laboratory.auth.observatorio.ia.api.dto.ChatResponse;
import com.laboratory.auth.observatorio.ia.api.dto.ConsultaResultado;
import com.laboratory.auth.observatorio.ia.api.dto.FuenteRecuperada;
import com.laboratory.auth.observatorio.ia.client.GeminiClient;
import com.laboratory.auth.observatorio.ia.conector.ExcelActivoConector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orquestador del asistente. Enruta cada pregunta hacia datos estructurados
 * (text-to-SQL), diccionario de indicadores o recuperacion documental (RAG),
 * compone la respuesta con fuentes y registra la interaccion (HU-01..10, HU-13).
 */
@Service
@RequiredArgsConstructor
public class IaChatService {

    public static final String LEYENDA_IA =
            "Contenido generado por IA. Verifique las fuentes citadas antes de su uso oficial.";

    /**
     * Regla anti-invencion. Comparte el mismo texto en el chat y en los borradores para
     * que las dos rutas no puedan divergir.
     *
     * <p>La parte de "el contexto es el inventario completo" no es decorativa: el
     * conector garantiza que todas las hojas y columnas del Excel activo llegan al
     * modelo, asi que puede afirmar con certeza que un campo no existe.
     */
    private static final String REGLAS_FUENTE = """
            Eres el asistente del Observatorio de Investigacion. Responde en espanol, claro y breve.

            FUENTE UNICA
            - La unica fuente de datos es el Excel ACTIVO que se indica en el contexto.
            - Ese Excel es el inventario completo del archivo: si un campo no aparece en el
              contexto, no existe en el archivo. Puedes afirmar que no existe.
            - No uses tu conocimiento general sobre el tema, ni informacion de archivos
              anteriores, ni de internet.

            PROHIBIDO INVENTAR
            - No inventes cifras, proyectos, investigadores, grupos, fechas, identificadores
              ni relaciones entre datos.
            - No completes un dato faltante con un valor plausible ni lo estimas.
            - No presentes como dato del archivo ninguna afirmacion que no puedas senalar
              literalmente en el contexto.

            CUANDO FALTE INFORMACION
            - Si el dato no esta en el Excel, dilo en primera persona y explica cual falta:
              "No puedo determinar <lo que se pregunta> porque el archivo activo no contiene
              <el campo o relacion que se necesita>."
            - Si los datos son insuficientes para responder, explica que falta y por que.
            - Si la pregunta es ambigua, enumera las interpretaciones posibles y que dato
              resolveria cada una, en vez de elegir una en silencio.
            - Ante la duda, responde "no se encuentra en el archivo activo" antes que una
              respuesta aproximada. Es preferible no responder a responder mal.

            SEPARAR DATO DE CONCLUSION
            - Cuando respondas con informacion del archivo, usa estas dos secciones:
              DATOS ENCONTRADOS: los hechos que aparecen literalmente en el Excel, citando
              la hoja y la columna cuando corresponda.
              DEDUCCIONES: tus interpretaciones, calculos o conclusiones a partir de esos
              datos. Nada de las deducciones puede presentarse como un dato del archivo.
            - Si no tienes nada que deducier, escribe "DEDUCCIONES: ninguna".
            """;

    private static final Pattern TABLA =
            Pattern.compile("\\b(?:from|join)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private final GeminiClient gemini;
    private final TextToSqlService textToSql;
    private final RagService rag;
    private final IndicadorService indicadores;
    private final AlcanceService alcance;
    private final AuditoriaService auditoria;
    private final ExcelActivoConector excelActivo;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Fuente declarada de todo lo que el asistente responde, para mostrarla en pantalla. */
    public String fuente() {
        return excelActivo.etiquetaFuente();
    }

    public ChatResponse responder(String email, String rol, String pregunta) {
        long inicio = System.currentTimeMillis();
        if (!gemini.disponible()) {
            return registrar(email, rol, pregunta, List.of(), "", false,
                    "El asistente no tiene configurada la clave del modelo (ia.gemini.api-key). "
                            + "Contacte al administrador del sistema.", inicio);
        }
        if (pregunta == null || pregunta.isBlank()) {
            return registrar(email, rol, pregunta, List.of(), "", true,
                    "Escribe una pregunta sobre los datos, indicadores o documentos del Observatorio.",
                    inicio);
        }
        if (alcance.fueraDeAlcance(pregunta)) {
            return registrar(email, rol, pregunta, List.of(), "", false,
                    alcance.mensajeFueraDeAlcance(), inicio);
        }

        Clasificacion clasificacion = clasificar(pregunta);
        ConsultaResultado datos = null;
        String sql = "";
        if ("DATOS".equals(clasificacion.intencion())) {
            // Sin Excel activo no hay fuente de la cual obtener cifras.
            if (!excelActivo.hayActivo()) {
                return registrar(email, rol, pregunta, List.of(), "", true,
                        "No hay ningun Excel activo en el sistema, asi que no puedo responder "
                                + "preguntas sobre los datos del Observatorio. Un administrador debe "
                                + "cargar y activar el archivo institucional.", inicio);
            }
            try {
                datos = clasificacion.sql() == null || clasificacion.sql().isBlank()
                        ? textToSql.consultar(pregunta)
                        : textToSql.ejecutar(clasificacion.sql());
                sql = datos.sql();
            } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
                datos = null; // SQL invalido o no ejecutable: se responde con documentos.
            }
            if (datos != null && datos.vacio()) {
                datos = null; // Sin coincidencias: los documentos cargados pueden responder.
            }
        }

        // HU-06/HU-08: los documentos autorizados se consultan siempre; antes se
        // omitian cuando la pregunta se clasificaba como DATOS y el SQL salia vacio.
        List<FuenteRecuperada> fuentes = rag.recuperar(pregunta, rol);
        List<ObjectNode> indicadoresEncontrados = indicadores.buscar(pregunta);
        if (datos == null && fuentes.isEmpty() && indicadoresEncontrados.isEmpty()) {
            return registrar(email, rol, pregunta, List.of(), sql, true,
                    "No encontre esa informacion en las fuentes autorizadas del Observatorio "
                            + "(datos estructurados del Excel activo, diccionario de indicadores, "
                            + "contenido publicado o documentos cargados). No genero contenido "
                            + "especulativo. Si cree que el dato deberia existir, digame que campo "
                            + "busca y revisamos si el archivo activo lo contiene.", inicio);
        }

        String contexto = datos == null ? "" : contextoDatos(datos);
        String respuesta = componer(pregunta, contexto, fuentes);
        if (!indicadoresEncontrados.isEmpty()) {
            respuesta = respuesta + "\n\n" + textoIndicadores(indicadoresEncontrados);
        }
        return registrar(email, rol, pregunta, fuentes, sql, true, respuesta, inicio);
    }

    private String contextoDatos(ConsultaResultado resultado) {
        return "Fuente de las cifras: Excel ACTIVO " + excelActivo.etiquetaFuente()
                + ". Se consultaron las tablas (" + tablas(resultado.sql())
                + "), que en este momento reflejan unicamente ese archivo.\n"
                + "Columnas: " + String.join(", ", resultado.columnas()) + "\n"
                + "Filas:\n" + filasComoTexto(resultado);
    }

    public BorradorResponse generarBorrador(String email, String rol, BorradorRequest request) {
        long inicio = System.currentTimeMillis();
        List<FuenteRecuperada> fuentes = rag.recuperar(request.tema(), rol);
        // El borrador tambien se apoya en el Excel activo, no solo en documentos.
        StringBuilder contexto = new StringBuilder();
        String estructura = excelActivo.contexto();
        if (!estructura.isBlank()) {
            contexto.append(estructura).append('\n');
        } else {
            contexto.append("No hay ningun Excel activo en el sistema.\n");
        }
        if (excelActivo.hayActivo()) {
            try {
                ConsultaResultado datos = textToSql.consultar(request.tema());
                if (!datos.vacio()) {
                    contexto.append("Cifras consultadas del Excel ACTIVO ")
                            .append(excelActivo.etiquetaFuente()).append(":\n")
                            .append(filasComoTexto(datos)).append('\n');
                }
            } catch (RuntimeException ignored) {
                // Sin datos estructurados; el borrador se apoya en documentos.
            }
        }
        String sistema = REGLAS_FUENTE + "\n"
                + "- El borrador es institucional y se apoya unicamente en el contexto entregado.\n"
                + "- Estructura: titulo, resumen, hallazgos y conclusiones. Los hallazgos deben\n"
                + "  distinguir que dato del archivo sostiene cada afirmacion.\n"
                + "- Si el contexto no alcanza para alguna de las secciones, escribe que no hay\n"
                + "  informacion disponible en el archivo activo.\n";
        String usuario = "Tema: " + request.tema() + "\nFiltros: " + String.join(", ", request.filtros())
                + "\n\n" + contexto + "\n" + contextoDocumental(fuentes, List.of());
        String borrador = gemini.disponible()
                ? gemini.generar(sistema, LEYENDA_IA + "\n\n" + usuario)
                : "El asistente no tiene configurada la clave del modelo (ia.gemini.api-key).";
        List<String> citas = new ArrayList<>(fuentes.stream().map(FuenteRecuperada::cita).toList());
        auditoria.registrar(email, rol, "Borrador: " + request.tema(), fuentes, "",
                borrador, true, System.currentTimeMillis() - inicio);
        return new BorradorResponse(borrador, citas, LEYENDA_IA);
    }

    private String componer(String pregunta, String contextoDatos, List<FuenteRecuperada> fuentes) {
        String sistema = REGLAS_FUENTE + "\n"
                + "- Cuando cites un fragmento documental, menciona el documento y su referencia.\n"
                + "- Cierra siempre con la linea: Fuente: <nombre del Excel activo>.\n"
                + "- La respuesta es interna al Observatorio: no la redactes como un texto\n"
                + "  dirigido a un destinatario externo.";
        StringBuilder sb = new StringBuilder();
        sb.append("Pregunta: ").append(pregunta).append("\n\n");
        String estructura = excelActivo.contexto();
        if (!estructura.isBlank()) {
            sb.append(estructura).append("\n");
        } else {
            sb.append("No hay ningun Excel activo en el sistema. No puedes afirmar cifras del "
                    + "Observatorio: dilo y pide al administrador que cargue el archivo.\n\n");
        }
        if (contextoDatos != null && !contextoDatos.isBlank()) {
            sb.append(contextoDatos).append("\n\n");
        }
        if (fuentes != null && !fuentes.isEmpty()) {
            sb.append("Fragmentos autorizados:\n");
            for (FuenteRecuperada f : fuentes) {
                sb.append("- [").append(f.cita()).append("] ").append(f.contenido()).append('\n');
            }
        }
        return gemini.generar(sistema, sb.toString());
    }

    private String textoIndicadores(List<ObjectNode> encontrados) {
        StringBuilder sb = new StringBuilder("Indicadores del diccionario:\n");
        for (ObjectNode i : encontrados) {
            sb.append("- ").append(i.path("nombre").asText()).append(": ")
                    .append(i.path("definicion").asText()).append(" Formula: ")
                    .append(i.path("formula").asText()).append(" Unidad: ")
                    .append(i.path("unidad").asText()).append(". Fuente: ")
                    .append(i.path("fuente").asText()).append(" Enlace: ")
                    .append(i.path("enlace").asText()).append('\n');
        }
        return sb.toString().trim();
    }

    private String contextoDocumental(List<FuenteRecuperada> fuentes, List<ObjectNode> indicadores) {
        StringBuilder sb = new StringBuilder();
        if (fuentes != null && !fuentes.isEmpty()) {
            sb.append("Fragmentos autorizados:\n");
            for (FuenteRecuperada f : fuentes) {
                sb.append("- [").append(f.cita()).append("] ").append(f.contenido()).append('\n');
            }
        }
        if (indicadores != null && !indicadores.isEmpty()) {
            sb.append(textoIndicadores(indicadores)).append('\n');
        }
        return sb.toString();
    }

    private Clasificacion clasificar(String pregunta) {
        String sistema = """
                Clasifica la pregunta de un usuario del Observatorio. Devuelve UNICAMENTE un JSON
                valido con esta forma: {"intencion":"DATOS|DOCUMENTO","sql":""}.
                Usa "DATOS" si la pregunta se puede responder contando, sumando, filtrando o
                listando registros del esquema (proyectos, grupos, investigadores). En ese caso
                escribe en "sql" una consulta PostgreSQL de SOLO LECTURA (SELECT o WITH) valida.
                Usa "DOCUMENTO" para explicaciones, definiciones, contenido institucional o
                documentos. Sin texto adicional.
                Esquema:
                """ + textToSql.descripcionEsquema();
        String crudo = gemini.generar(sistema, pregunta);
        try {
            String json = crudo.substring(crudo.indexOf('{'), crudo.lastIndexOf('}') + 1);
            JsonNode n = mapper.readTree(json);
            String intencion = n.path("intencion").asText();
            return new Clasificacion(intencion.isBlank() ? "DOCUMENTO" : intencion.toUpperCase(),
                    n.path("sql").asText());
        } catch (RuntimeException e) {
            return new Clasificacion("DOCUMENTO", "");
        }
    }

    private ChatResponse registrar(String email, String rol, String pregunta,
                                   List<FuenteRecuperada> fuentes, String sql,
                                   boolean enAlcance, String respuesta, long inicio) {
        long latencia = System.currentTimeMillis() - inicio;
        auditoria.registrar(email, rol, pregunta, fuentes, sql, respuesta, enAlcance, latencia);
        List<String> citas = fuentes.stream().map(FuenteRecuperada::cita).collect(Collectors.toList());
        return new ChatResponse(respuesta, enAlcance, citas, sql, latencia, fuente());
    }

    private String filasComoTexto(ConsultaResultado resultado) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(" | ", resultado.columnas())).append('\n');
        for (List<Object> fila : resultado.filas()) {
            sb.append(fila.stream().map(v -> v == null ? "" : String.valueOf(v))
                    .collect(Collectors.joining(" | "))).append('\n');
        }
        return sb.toString();
    }

    private String tablas(String sql) {
        Matcher m = TABLA.matcher(sql);
        List<String> tablas = new ArrayList<>();
        while (m.find()) {
            tablas.add(m.group(1));
        }
        return tablas.isEmpty() ? "consulta" : String.join(", ", tablas);
    }

    private record Clasificacion(String intencion, String sql) {
    }
}
