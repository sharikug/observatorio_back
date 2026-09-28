package com.laboratory.auth.observatorio.api.rest;

import com.laboratory.auth.observatorio.api.dto.ExcelHistorial;
import com.laboratory.auth.observatorio.api.dto.ReporteArchivo;
import com.laboratory.auth.observatorio.service.ExcelActivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel activo e historial. Activar valida primero: si el archivo tiene errores
 * criticos el Excel anterior sigue siendo el activo.
 */
@RestController
@RequestMapping("/api/observatorio/excel")
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelActivoService excelActivoService;

    @PostMapping("/activar")
    public ExcelActivoService.Resultado activar(@RequestParam("archivo") MultipartFile archivo,
                                               @RequestParam(value = "confirmar", defaultValue = "false")
                                               boolean confirmar,
                                               Authentication authentication) {
        return excelActivoService.activar(archivo, authentication.getName(), confirmar);
    }

    @GetMapping("/historial")
    public ExcelHistorial historial() {
        return excelActivoService.historial();
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable String id) {
        ReporteArchivo archivo = excelActivoService.contenido(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombre() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo.contenido());
    }
}
