package com.laboratory.auth.observatorio.ia.api.rest;

import com.laboratory.auth.observatorio.ia.api.dto.ContenidoRequest;
import com.laboratory.auth.observatorio.ia.api.dto.Fragmento;
import com.laboratory.auth.observatorio.ia.service.AuditoriaService;
import com.laboratory.auth.observatorio.ia.service.IngestaService;
import com.laboratory.auth.observatorio.ia.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class IaAdminController {

    /** Coincide con spring.servlet.multipart.max-file-size. */
    private static final long TAMANO_MAXIMO = 20L * 1024 * 1024;

    private final IngestaService ingestaService;
    private final RagService ragService;
    private final AuditoriaService auditoriaService;

    /** Extensiones que el pipeline sabe extraer. Es la lista blanca de HU-29. */
    private static final Set<String> EXTENSIONES = Set.of("pdf", "docx", "xlsx", "xls", "txt", "md", "csv");

    /** HU-06: carga e indexa un documento autorizado. */
    @PostMapping("/documentos")
    public Map<String, Object> cargar(@RequestParam("archivo") MultipartFile archivo,
                                      @RequestParam(value = "roles", required = false) List<String> roles,
                                      Authentication authentication) {
        validarArchivo(archivo);
        String nombre = archivo.getOriginalFilename();
        List<Fragmento> fragmentos = ingestaService.extraer(archivo);
        if (fragmentos.isEmpty()) {
            throw new IllegalArgumentException("No se extrajo texto indexable del documento");
        }
        String tipo = extension(nombre).toUpperCase();
        String id = ragService.indexar(nombre, tipo, "DOCUMENTO", authentication.getName(),
                roles, fragmentos);
        return Map.of("id", id, "nombre", nombre, "estado", RagService.DISPONIBLE,
                "fragmentos", fragmentos.size(), "mensaje", "Documento disponible para consulta.");
    }

    /**
     * HU-29: nada entra al pipeline sin pasar por aqui. El nombre se recorta a su
     * nombre de archivo para que no pueda inyectar rutas, y el contenido se limita por
     * cabecera y por tamano, no solo por la extension.
     */
    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        if (archivo.getSize() > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El archivo supera el tamano maximo de "
                    + (TAMANO_MAXIMO / (1024 * 1024)) + " MB");
        }
        String ext = extension(archivo.getOriginalFilename());
        if (!EXTENSIONES.contains(ext)) {
            throw new IllegalArgumentException("Formato no soportado: ." + ext
                    + ". Se aceptan " + String.join(", ", EXTENSIONES));
        }
    }

    private String extension(String nombre) {
        String limpio = nombre == null ? "" : nombre.replace('\\', '/');
        limpio = limpio.substring(limpio.lastIndexOf('/') + 1).trim();
        int punto = limpio.lastIndexOf('.');
        return punto < 0 ? "" : limpio.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    /** HU-05: indexa contenido publicado del sitio institucional. */
    @PostMapping("/contenido")
    public Map<String, Object> contenido(@RequestBody ContenidoRequest request) {
        List<Fragmento> fragmentos = ingestaService.fragmentar(request.texto(), request.url());
        if (fragmentos.isEmpty()) {
            throw new IllegalArgumentException("El contenido esta vacio");
        }
        String id = ragService.indexar(
                request.titulo() == null ? request.url() : request.titulo(),
                "WEB", "SITIO", null, request.roles(), fragmentos);
        return Map.of("id", id, "estado", RagService.DISPONIBLE, "fragmentos", fragmentos.size());
    }

    @GetMapping("/documentos")
    public List<Map<String, Object>> documentos() {
        return ragService.documentos();
    }

    /** HU-06: estado de indexacion de un documento concreto. */
    @GetMapping("/documentos/{id}/estado")
    public Map<String, Object> estadoDocumento(@PathVariable String id) {
        return ragService.estado(id);
    }

    /** HU-10: consulta de trazabilidad. */
    @GetMapping("/auditoria")
    public List<Map<String, Object>> auditoria(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return auditoriaService.listar(desde(desde, -30), hasta(hasta, 0));
    }

    @GetMapping("/auditoria/export")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        String csv = auditoriaService.exportarCsv(desde(desde, -30), hasta(hasta, 0));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria-ia.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private LocalDateTime desde(String valor, int dias) {
        return valor == null || valor.isBlank()
                ? LocalDateTime.now().plusDays(dias)
                : LocalDateTime.parse(valor);
    }

    private LocalDateTime hasta(String valor, int dias) {
        return valor == null || valor.isBlank()
                ? LocalDateTime.now().plusDays(dias)
                : LocalDateTime.parse(valor);
    }
}
