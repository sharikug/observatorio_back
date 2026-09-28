package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.ExcelCargadoItem;
import com.laboratory.auth.observatorio.api.dto.ExcelHistorial;
import com.laboratory.auth.observatorio.api.dto.ImportarResumen;
import com.laboratory.auth.observatorio.api.dto.ValidacionResultado;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El Excel activo es la garantia central del sistema: nunca hay dos, y un archivo con
 * errores criticos no desplaza al vigente.
 */
class ExcelActivoServiceTest {

    /** SQL ejecutados contra la base, en orden, para comprobar la secuencia. */
    private final List<String> sql = new ArrayList<>();

    @Test
    void archivoConErroresCriticosNoDesplazaAlExcelVigente() throws Exception {
        ExcelActivoService servicio = servicio(resultado(false, 1, 2, 3));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.activar(archivo("roto.xlsx", libroConColumnaInvalida()), "admin", true));

        assertTrue(e.getMessage().contains("criticos"), e.getMessage());
        assertTrue(e.getMessage().contains("no fue modificado"), e.getMessage());
        assertTrue(sql.isEmpty(), "no debe escribirse nada en la base: " + sql);
        verify(importador, never()).limpiar();
        verify(importador, never()).importar(any());
    }

    @Test
    void advertenciasExigenConfirmacionExplicita() throws Exception {
        ExcelActivoService servicio = servicio(resultado(false, 0, 3, 3));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.activar(archivo("avisos.xlsx", libroValido()), "admin", false));

        assertTrue(e.getMessage().contains("confirmar"), e.getMessage());
        verify(importador, never()).importar(any());
    }

    @Test
    void activarDesplazaElAnteriorLimpiaEImportaEnEseOrden() throws Exception {
        ExcelActivoService servicio = servicio(resultado(true, 0, 0, 0));

        servicio.activar(archivo("nuevo.xlsx", libroValido()), "admin", false);

        assertTrue(sql.get(0).startsWith("UPDATE excel_cargado SET estado ="),
                "el Excel vigente debe dejar de ser activo ANTES de registrar el nuevo, "
                        + "para no violar el indice unico de ACTIVO. Orden real: " + sql);
        assertTrue(sql.get(sql.size() - 1).startsWith("INSERT INTO excel_cargado"),
                "el nuevo Excel se registra al final. Orden real: " + sql);
        // La purga debe ocurrir entre el desplazamiento y la importacion; si no, la base
        // conservaria filas del archivo anterior mezcladas con las del nuevo.
        InOrder orden = inOrder(importador);
        orden.verify(importador).limpiar();
        orden.verify(importador).importar(any());
    }

    @Test
    void laValidacionCorreEnElServidorAunqueElFrontendDigaQueNo() throws Exception {
        ExcelActivoService servicio = servicio(resultado(false, 2, 0, 2));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.activar(archivo("x.xlsx", libroValido()), "admin", true));
        verify(importador, never()).limpiar();
    }

    @Test
    void sinExcelActivoElHistorialNoArrancaConAlgoFalso() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<ExcelCargadoItem>>any()))
                .thenReturn(List.of());

        ExcelHistorial h = new ExcelActivoService(jdbc, null, null).historial();

        assertNull(h.activo());
        assertFalse(h.hayActivo());
    }

    @Test
    void limpiarBorraLoImportadoYPreservaLoManual() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new ImportadorService(jdbc).limpiar();

        // proyecto es la unica tabla con columna origen: solo se purga lo IMPORTADO.
        verify(jdbc).update("DELETE FROM proyecto WHERE origen <> 'MANUAL'");
        // Las demas no marcan el origen, se vacian enteras.
        verify(jdbc).update("DELETE FROM grupo");
        verify(jdbc).update("DELETE FROM investigador");
        // Exactamente una tabla se purga de forma selectiva.
        verify(jdbc, times(1)).update(ArgumentMatchers.argThat(
                (String s) -> s != null && s.contains("origen")));
    }

    // --- utilidades -----------------------------------------------------------

    private final ImportadorService importador = mock(ImportadorService.class);

    private ExcelActivoService servicio(ValidacionResultado validacion) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(inv -> {
            sql.add(inv.getArgument(0));
            return 1;
        });
        when(importador.importar(any()))
                .thenReturn(new ImportarResumen(1, 0, 0, List.of()));
        ExcelValidacionService validador = mock(ExcelValidacionService.class);
        when(validador.validar(any())).thenReturn(validacion);
        return new ExcelActivoService(jdbc, validador, importador);
    }

    private ValidacionResultado resultado(boolean valido, int criticas, int advertencias, int total) {
        return new ValidacionResultado("a.xlsx", valido, criticas == 0, total, criticas, advertencias,
                List.of(), List.of());
    }

    private MockMultipartFile archivo(String nombre, Workbook wb) throws IOException {
        return new MockMultipartFile("archivo", nombre,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes(wb));
    }

    private static Workbook libroValido() {
        Workbook wb = new XSSFWorkbook();
        for (ReglasImportacion.Regla r : ReglasImportacion.HOJAS) {
            Row h = wb.createSheet(r.hoja()).createRow(0);
            Row d = wb.getSheet(r.hoja()).createRow(1);
            for (int i = 0; i < r.claves().size(); i++) {
                h.createCell(i).setCellValue(r.claves().get(i));
                d.createCell(i).setCellValue("V-" + r.hoja() + "-" + i);
            }
        }
        return wb;
    }

    private static Workbook libroConColumnaInvalida() {
        Workbook wb = libroValido();
        Row h = wb.getSheet("Proyectos").getRow(0);
        h.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("columna_que_no_existe");
        return wb;
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
