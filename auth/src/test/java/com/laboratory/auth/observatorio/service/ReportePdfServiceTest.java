package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.ReporteDocumento;
import com.laboratory.auth.observatorio.api.dto.ReporteGrafico;
import com.laboratory.auth.observatorio.api.dto.ReporteIndicador;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportePdfServiceTest {

    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Test
    void generaPdfConGraficasYTabla() {
        ReporteDocumento documento = new ReporteDocumento(
                "Proyectos de investigacion",
                List.of("Facultad: Ingenieria"),
                List.of(new ReporteIndicador("Proyectos", "1")),
                List.of("Codigo", "Proyecto"),
                List.of(List.of("P1", "Proyecto uno")),
                List.of(new ReporteGrafico("Evolucion del portafolio", PNG_1X1)),
                "Observatorio de Investigacion");

        byte[] pdf = new ReportePdfService().generar(documento);

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(pdf.length > 500, "el PDF debe tener contenido");
    }

    @Test
    void generaPdfSinDatosNoFalla() {
        ReporteDocumento documento = new ReporteDocumento(
                "Grupos de investigacion", List.of(), List.of(), List.of(), List.of(), List.of(),
                "Observatorio de Investigacion");

        byte[] pdf = new ReportePdfService().generar(documento);

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }
}
