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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class IaAdminController {

    private final IngestaService ingestaService;
    private final RagService ragService;
    private final AuditoriaService auditoriaService;

    /** HU-06: carga e indexa un documento autorizado. */
    @PostMapping("/documentos")
    public Map<String, Object> cargar(@RequestParam("archivo") MultipartFile archivo,
                                      @RequestParam(value = "roles", required = false) List<String> roles) {
        if (archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        String nombre = archivo.getOriginalFilename() == null ? "documento" : archivo.getOriginalFilename();
        List<Fragmento> fragmentos = ingestaService.extraer(archivo);
        if (fragmentos.isEmpty()) {
            throw new IllegalArgumentException("No se extrajo texto indexable del documento");
        }
        String tipo = nombre.contains(".")
                ? nombre.substring(nombre.lastIndexOf('.') + 1).toUpperCase() : "DESCONOCIDO";
        String id = ragService.indexar(nombre, tipo, "DOCUMENTO", roles, fragmentos);
        return Map.of("id", id, "nombre", nombre, "estado", "INDEXADO", "fragmentos", fragmentos.size());
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
                "WEB", "SITIO", request.roles(), fragmentos);
        return Map.of("id", id, "estado", "INDEXADO", "fragmentos", fragmentos.size());
    }

    @GetMapping("/documentos")
    public List<Map<String, Object>> documentos() {
        return ragService.documentos();
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
