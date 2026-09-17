package com.laboratory.auth.observatorio.api.rest;

import com.laboratory.auth.observatorio.api.dto.GruposDashboard;
import com.laboratory.auth.observatorio.api.dto.ImportarResumen;
import com.laboratory.auth.observatorio.api.dto.ProyectoRow;
import com.laboratory.auth.observatorio.service.DashboardService;
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
    private final DashboardService dashboardService;

    @PostMapping("/importar")
    public ImportarResumen importar(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        return importadorService.importar(archivo);
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
