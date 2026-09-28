package com.laboratory.auth.observatorio.api.rest;

import com.laboratory.auth.observatorio.api.dto.GruposDashboard;
import com.laboratory.auth.observatorio.api.dto.ImportarResumen;
import com.laboratory.auth.observatorio.api.dto.ProyectoRow;
import com.laboratory.auth.observatorio.api.dto.ValidacionResultado;
import com.laboratory.auth.observatorio.service.DashboardService;
import com.laboratory.auth.observatorio.service.ExcelValidacionService;
import com.laboratory.auth.observatorio.service.ImportadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/observatorio")
@RequiredArgsConstructor
public class ObservatorioController {

    private final ImportadorService importadorService;
    private final ExcelValidacionService excelValidacionService;
    private final DashboardService dashboardService;

    /**
     * Solo valida: no escribe nada en la base de datos. El frontend muestra el resultado y
     * el usuario decide si continua.
     */
    @PostMapping("/importar/validar")
    public ValidacionResultado validar(@RequestParam("archivo") MultipartFile archivo) {
        exigirArchivo(archivo);
        return excelValidacionService.validar(archivo);
    }

    @PostMapping("/importar")
    public ImportarResumen importar(@RequestParam("archivo") MultipartFile archivo) {
        exigirArchivo(archivo);
        // No se confia en el frontend: se vuelve a validar y los errores criticos bloquean.
        ValidacionResultado v = excelValidacionService.validar(archivo);
        if (!v.puedeContinuar()) {
            throw new IllegalArgumentException("El archivo no puede procesarse debido a "
                    + v.criticas() + " errores criticos. " + v.resumenCriticos());
        }
        return importadorService.importar(archivo);
    }

    private void exigirArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
    }

    @GetMapping("/proyectos")
    public List<ProyectoRow> proyectos() {
        return dashboardService.proyectos();
    }

    @GetMapping("/grupos")
    public GruposDashboard grupos() {
        return dashboardService.grupos();
    }
}
