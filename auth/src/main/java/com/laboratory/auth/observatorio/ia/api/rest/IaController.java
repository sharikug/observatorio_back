package com.laboratory.auth.observatorio.ia.api.rest;

import com.laboratory.auth.observatorio.ia.api.dto.BorradorRequest;
import com.laboratory.auth.observatorio.ia.api.dto.BorradorResponse;
import com.laboratory.auth.observatorio.ia.api.dto.ChatRequest;
import com.laboratory.auth.observatorio.ia.api.dto.ChatResponse;
import com.laboratory.auth.observatorio.ia.api.dto.ExportarBorradorRequest;
import com.laboratory.auth.observatorio.ia.service.BorradorExportService;
import com.laboratory.auth.observatorio.ia.service.ConversacionService;
import com.laboratory.auth.observatorio.ia.service.ConectorService;
import com.laboratory.auth.observatorio.ia.service.IaChatService;
import com.laboratory.auth.observatorio.ia.service.IndicadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class IaController {

    /** Cabecera con el identificador del navegador para las consultas anonimas. */
    private static final String ANON_HEADER = "X-Anon-Id";

    /** Llega desde el cliente y se guarda en base de datos: solo letras, digitos y guion. */
    private static final Pattern ANON_VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final IaChatService chatService;
    private final IndicadorService indicadorService;
    private final ConectorService conectorService;
    private final ConversacionService conversacionService;
    private final BorradorExportService exportService;

    /**
     * Identidad de quien pregunta. Sin iniciar sesion, la peticion es anonima y la
     * separacion entre conversaciones la da la cabecera X-Anon-Id, que el navegador
     * genera una vez y conserva.
     *
     * <p>Si la cabecera falta o viene manipulada se genera un identificador propio: dos
     * visitantes sin cabecera NO deben acabar en el mismo historial compartido.
     */
    private String usuario(Authentication authentication, String anonId) {
        if (!esAnonimo(authentication)) {
            return authentication.getName();
        }
        return "anonimo:" + (anonId != null && ANON_VALIDO.matcher(anonId).matches()
                ? anonId : UUID.randomUUID());
    }

    private static boolean esAnonimo(Authentication authentication) {
        // Nombre que Spring asigna a las peticiones permitAll sin sesion iniciada.
        return authentication == null || "anonymousUser".equals(authentication.getName());
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request,
                             @RequestHeader(value = ANON_HEADER, required = false) String anonId,
                             Authentication authentication) {
        boolean anonimo = esAnonimo(authentication);
        return chatService.responder(usuario(authentication, anonId),
                anonimo ? "EXTERNO" : rol(authentication),
                request.pregunta(), request.conversacionId());
    }

    /** HU-30: el usuario abre una conversacion nueva y luego la continua. */
    @PostMapping("/conversaciones")
    public Map<String, Object> nuevaConversacion(
            @RequestHeader(value = ANON_HEADER, required = false) String anonId,
            Authentication authentication) {
        return Map.of("id", conversacionService.crear(usuario(authentication, anonId)));
    }

    @GetMapping("/conversaciones")
    public List<Map<String, Object>> conversaciones(
            @RequestHeader(value = ANON_HEADER, required = false) String anonId,
            Authentication authentication) {
        return conversacionService.listar(usuario(authentication, anonId));
    }

    @GetMapping("/conversaciones/{id}")
    public List<Map<String, Object>> conversacion(@PathVariable String id,
                                                  @RequestHeader(value = ANON_HEADER, required = false) String anonId,
                                                  Authentication authentication) {
        return conversacionService.mensajes(usuario(authentication, anonId), id);
    }

    @DeleteMapping("/conversaciones/{id}")
    public void borrarConversacion(@PathVariable String id,
                                   @RequestHeader(value = ANON_HEADER, required = false) String anonId,
                                   Authentication authentication) {
        conversacionService.borrar(usuario(authentication, anonId), id);
    }

    @PostMapping("/reportes")
    public BorradorResponse borrador(@RequestBody BorradorRequest request,
                                     Authentication authentication) {
        return chatService.generarBorrador(authentication.getName(), rol(authentication), request);
    }

    /** HU-07: descarga el informe en PDF o Word, con leyenda y fuentes. */
    @PostMapping("/reportes/exportar")
    public ResponseEntity<byte[]> exportar(@RequestBody ExportarBorradorRequest request,
                                           @RequestParam(defaultValue = "pdf") String formato) {
        boolean docx = "docx".equalsIgnoreCase(formato);
        if (!docx && !"pdf".equalsIgnoreCase(formato)) {
            throw new IllegalArgumentException("Formato no soportado: use pdf o docx");
        }
        byte[] archivo = docx
                ? exportService.docx(request.tema(), request.borrador(), request.fuentes(), request.leyenda())
                : exportService.pdf(request.tema(), request.borrador(), request.fuentes(), request.leyenda());
        String nombre = "informe-observatorio-ia." + (docx ? "docx" : "pdf");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombre + "\"")
                .contentType(docx
                        ? MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        : MediaType.APPLICATION_PDF)
                .body(archivo);
    }

    @GetMapping("/indicadores")
    public List<Map<String, Object>> indicadores() {
        return indicadorService.listar();
    }

    @GetMapping("/conectores")
    public List<Map<String, String>> conectores() {
        return conectorService.activos().stream()
                .map(c -> Map.of("id", c.id(), "descripcion", c.descripcion()))
                .toList();
    }

    static String rol(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return "EXTERNO";
        }
        String autoridad = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).findFirst().orElse("EXTERNO");
        return autoridad.startsWith("ROLE_") ? autoridad.substring(5) : autoridad;
    }
}
