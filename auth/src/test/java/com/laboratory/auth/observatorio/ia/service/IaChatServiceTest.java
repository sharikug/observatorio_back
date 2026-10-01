package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.BorradorRequest;
import com.laboratory.auth.observatorio.ia.api.dto.ChatResponse;
import com.laboratory.auth.observatorio.ia.api.dto.ConsultaResultado;
import com.laboratory.auth.observatorio.ia.api.dto.FuenteRecuperada;
import com.laboratory.auth.observatorio.ia.client.OpenRouterClient;
import com.laboratory.auth.observatorio.ia.conector.ExcelActivoConector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IaChatServiceTest {

    @Test
    void consultaElDocumentoCargadoCuandoLaConsultaDeDatosSaleVacia() {
        OpenRouterClient modelo = mock(OpenRouterClient.class);
        TextToSqlService textToSql = mock(TextToSqlService.class);
        RagService rag = mock(RagService.class);
        IndicadorService indicadores = mock(IndicadorService.class);
        AlcanceService alcance = mock(AlcanceService.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);

        when(modelo.disponible()).thenReturn(true);
        when(modelo.generar(anyString(), anyString()))
                .thenReturn("{\"intencion\":\"DATOS\",\"sql\":\"SELECT 1\"}");
        when(textToSql.ejecutar(anyString()))
                .thenReturn(new ConsultaResultado("SELECT 1", List.of(), List.of()));
        when(rag.recuperar(anyString(), any()))
                .thenReturn(List.of(new FuenteRecuperada(
                        "d1", "informe.pdf", "pagina 1", "contenido del informe", 0.9)));
        when(indicadores.buscar(anyString())).thenReturn(List.of());
        when(alcance.fueraDeAlcance(anyString())).thenReturn(false);
        ExcelActivoConector excel = mock(ExcelActivoConector.class);
        when(excel.hayActivo()).thenReturn(true);
        when(excel.etiquetaFuente()).thenReturn("observatorio.xlsx (cargado el 2026-09-27 10:00)");
        when(excel.contexto()).thenReturn("ARCHIVO FUENTE ACTIVO: observatorio.xlsx");

        IaChatService servicio = new IaChatService(modelo, textToSql, rag, indicadores, alcance,
                auditoria, excel, new ConversacionService(mock(JdbcTemplate.class)));
        ChatResponse respuesta = servicio.responder("analista@ucundinamarca.edu.co", "ANALISTA",
                "Que dice el informe cargado?", null);

        assertFalse(respuesta.respuesta().contains("No encontre"),
                "debe responder con el documento, no con el mensaje de sin resultados");
        assertEquals(List.of("informe.pdf (pagina 1)"), respuesta.fuentes());
        assertEquals("observatorio.xlsx (cargado el 2026-09-27 10:00)", respuesta.fuenteDatos());
    }

    @Test
    void sinExcelActivoNoAfilaCifrasDelObservatorio() {
        OpenRouterClient modelo = mock(OpenRouterClient.class);
        TextToSqlService textToSql = mock(TextToSqlService.class);
        RagService rag = mock(RagService.class);
        IndicadorService indicadores = mock(IndicadorService.class);
        AlcanceService alcance = mock(AlcanceService.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        ExcelActivoConector excel = mock(ExcelActivoConector.class);

        when(modelo.disponible()).thenReturn(true);
        when(modelo.generar(anyString(), anyString()))
                .thenReturn("{\"intencion\":\"DATOS\",\"sql\":\"SELECT 1\"}");
        when(alcance.fueraDeAlcance(anyString())).thenReturn(false);
        when(excel.hayActivo()).thenReturn(false);
        when(excel.etiquetaFuente()).thenReturn("sin Excel activo");

        IaChatService servicio = new IaChatService(modelo, textToSql, rag, indicadores, alcance,
                auditoria, excel, new ConversacionService(mock(JdbcTemplate.class)));
        ChatResponse respuesta = servicio.responder("analista@ucundinamarca.edu.co", "ANALISTA",
                "Cuantos proyectos hay?", null);

        assertTrue(respuesta.respuesta().contains("No hay ningun Excel activo"), respuesta.respuesta());
        verify(textToSql, never()).ejecutar(anyString());
    }

    /**
     * El inventario del Excel activo debe viajarle al modelo como texto de sistema, porque
     * es lo que le permite decir "esta columna no existe" con certeza en vez de suponerlo.
     */
    @Test
    void elModeloRecibeLasReglasAntiInvencionYElInventarioDelExcel() {
        OpenRouterClient modelo = mock(OpenRouterClient.class);
        TextToSqlService textToSql = mock(TextToSqlService.class);
        RagService rag = mock(RagService.class);
        IndicadorService indicadores = mock(IndicadorService.class);
        AlcanceService alcance = mock(AlcanceService.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        ExcelActivoConector excel = mock(ExcelActivoConector.class);

        when(modelo.disponible()).thenReturn(true);
        // La primera llamada es el clasificador, la segunda compone la respuesta.
        when(modelo.generar(anyString(), anyString()))
                .thenReturn("{\"intencion\":\"DOCUMENTO\",\"sql\":\"\"}", "respuesta");
        when(textToSql.ejecutar(anyString()))
                .thenReturn(new ConsultaResultado("SELECT 1", List.of(), List.of()));
        when(rag.recuperar(anyString(), any())).thenReturn(List.of(
                new FuenteRecuperada("d1", "informe.pdf", "pagina 1", "contenido", 0.9)));
        when(indicadores.buscar(anyString())).thenReturn(List.of());
        when(alcance.fueraDeAlcance(anyString())).thenReturn(false);
        when(excel.hayActivo()).thenReturn(true);
        when(excel.etiquetaFuente()).thenReturn("observatorio.xlsx (cargado el 2026-09-27 10:00)");
        // Inventario sin columna de fecha: el caso de "cual es el proyecto mas antiguo".
        when(excel.contexto()).thenReturn(
                "ARCHIVO FUENTE ACTIVO: observatorio.xlsx\n"
                        + "Inventario completo del archivo\n\nHOJA: Proyectos | 2 filas de datos\n"
                        + "  COLUMNAS: codigo_proyecto | nombre_proyecto");

        new IaChatService(modelo, textToSql, rag, indicadores, alcance, auditoria, excel,
                new ConversacionService(mock(JdbcTemplate.class)))
                .responder("analista@ucundinamarca.edu.co", "ANALISTA", "Cual es el proyecto mas antiguo?", null);

        ArgumentCaptor<String> sistema = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(modelo, atLeastOnce()).generar(sistema.capture(), enviado.capture());

        String instrucciones = sistema.getAllValues().stream()
                .filter(s -> s.contains("PROHIBIDO INVENTAR")).findFirst().orElseThrow();
        assertTrue(instrucciones.contains("No inventes cifras"), instrucciones);
        assertTrue(instrucciones.contains("si un campo no aparece en el"), instrucciones);
        assertTrue(instrucciones.contains("DATOS ENCONTRADOS"), instrucciones);
        assertTrue(instrucciones.contains("DEDUCCIONES"), instrucciones);

// El compositor es el prompt mas largo: lleva el inventario del Excel, los datos
// consultados y los fragmentos. El clasificador es una linea con la pregunta.
String prompt = enviado.getAllValues().stream()
                .max(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new AssertionError(
                        "no se encontro ningun prompt: " + enviado.getAllValues()));
        assertTrue(prompt.contains("codigo_proyecto"), "el modelo debe ver el inventario real: " + prompt);
        assertFalse(prompt.contains("fecha"), "este archivo no tiene columna de fecha, y eso debe verse");
    }

    @Test
    void laReglaAntiInvencionTambienSeAplicaALosBorradores() {
        OpenRouterClient modelo = mock(OpenRouterClient.class);
        TextToSqlService textToSql = mock(TextToSqlService.class);
        RagService rag = mock(RagService.class);
        IndicadorService indicadores = mock(IndicadorService.class);
        AlcanceService alcance = mock(AlcanceService.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        ExcelActivoConector excel = mock(ExcelActivoConector.class);

        when(modelo.disponible()).thenReturn(true);
        when(modelo.generar(anyString(), anyString())).thenReturn("borrador");
        when(rag.recuperar(anyString(), any())).thenReturn(List.of());
        when(excel.hayActivo()).thenReturn(true);
        when(excel.etiquetaFuente()).thenReturn("observatorio.xlsx");
        when(excel.contexto()).thenReturn("ARCHIVO FUENTE ACTIVO: observatorio.xlsx\nHOJA: Proyectos");

        new IaChatService(modelo, textToSql, rag, indicadores, alcance, auditoria, excel,
                new ConversacionService(mock(JdbcTemplate.class)))
                .generarBorrador("admin@ucundinamarca.edu.co", "ADMINISTRADOR",
                        new BorradorRequest("produccion cientifica", List.of("2024")));

        ArgumentCaptor<String> sistema = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(modelo).generar(sistema.capture(), enviado.capture());
        assertTrue(sistema.getValue().contains("PROHIBIDO INVENTAR"), sistema.getValue());
        assertTrue(enviado.getValue().contains("HOJA: Proyectos"),
                "el borrador tambien debe recibir el Excel activo: " + enviado.getValue());
    }
}
