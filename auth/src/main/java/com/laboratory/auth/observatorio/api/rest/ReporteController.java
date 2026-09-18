package com.laboratory.auth.observatorio.api.rest;

import com.laboratory.auth.observatorio.api.dto.ReporteArchivo;
import com.laboratory.auth.observatorio.api.dto.ReporteDocumento;
import com.laboratory.auth.observatorio.api.dto.ReporteHistorialItem;
import com.laboratory.auth.observatorio.service.ReportePdfService;
import com.laboratory.auth.observatorio.service.ReporteService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/observatorio/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;
    private final ReportePdfService pdfService;

    @PostMapping("/generar")
    public ResponseEntity<byte[]> generar(@RequestBody ReporteDocumento documento,
                                          Authentication authentication) {
        byte[] pdf = pdfService.generar(documento);
        reporteService.guardar(authentication.getName(), documento, pdf);
        return respuesta(pdf, "informe-observatorio.pdf");
    }

    @GetMapping("/historial")
    public List<ReporteHistorialItem> historial(Authentication authentication) {
        return reporteService.historial(authentication.getName());
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable String id, Authentication authentication) {
        ReporteArchivo archivo = reporteService.archivo(id, authentication.getName());
        return respuesta(archivo.contenido(), archivo.nombre());
    }

    private ResponseEntity<byte[]> respuesta(byte[] contenido, String nombre) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenido);
    }
}
