package com.laboratory.auth.observatorio.ia.conector;

import com.laboratory.auth.observatorio.api.dto.ExcelCargadoItem;
import com.laboratory.auth.observatorio.api.dto.ReporteArchivo;
import com.laboratory.auth.observatorio.service.ExcelActivoService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentMatchers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El asistente debe recibir la estructura real del Excel activo, hoja por hoja, y
 * nunca datos de un archivo historico.
 */
class ExcelActivoConectorTest {

    private final ExcelActivoService excel = mock(ExcelActivoService.class);

    @Test
    void elContextoTraeHojaColumnasYRegistrosDelArchivoActivo() throws Exception {
        cuandoActivo(libroConDosHojas());

        String ctx = new ExcelActivoConector(excel).contexto();

        assertTrue(ctx.contains("ARCHIVO FUENTE ACTIVO: observatorio.xlsx"), ctx);
        assertTrue(ctx.contains("HOJA: Proyectos"), ctx);
        assertTrue(ctx.contains("HOJA: Grupos"), ctx);
        assertTrue(ctx.contains("COLUMNAS: codigo_proyecto | nombre_proyecto"), ctx);
        assertTrue(ctx.contains("PRY-001"), ctx);
        assertTrue(ctx.contains("Grupo de Biotecnologia"), ctx);
        // Las hojas van separadas: el nombre de una hoja nunca precede al contenido de otra.
        assertTrue(ctx.indexOf("HOJA: Proyectos") < ctx.indexOf("HOJA: Grupos"), ctx);
    }

    @Test
    void elContextoIndicaCuantasFilasTieneCadaHoja() throws Exception {
        cuandoActivo(libroConDosHojas());

        String ctx = new ExcelActivoConector(excel).contexto();

        assertTrue(ctx.contains("| 2 filas de datos"), ctx);
        assertTrue(ctx.contains("| 1 filas de datos"), ctx);
    }

    @Test
    void sinExcelActivoNoSeInventaFuente() {
        when(excel.activo()).thenReturn(Optional.empty());

        ExcelActivoConector conector = new ExcelActivoConector(excel);

        assertFalse(conector.hayActivo());
        assertEquals("", conector.contexto());
        assertEquals("sin Excel activo", conector.etiquetaFuente());
    }

    @Test
    void laMuestraSeAcotaParaNoReventarElPrompt() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Row h = wb.createSheet("Proyectos").createRow(0);
        h.createCell(0).setCellValue("codigo_proyecto");
        for (int i = 1; i <= 5000; i++) {
            wb.getSheet("Proyectos").createRow(i).createCell(0).setCellValue("PRY-" + i);
        }
        cuandoActivo(wb);

        String ctx = new ExcelActivoConector(excel).contexto();

        assertTrue(ctx.contains("5000 filas de datos"), "debe saber cuantas hay aunque no las muestre todas");
        assertFalse(ctx.contains("PRY-5000"), "la muestra se acota, no se vuelca el archivo entero");
        assertTrue(ctx.length() < 12000, "el contexto debe estar acotado, iba en " + ctx.length());
    }

    @Test
    void laEtiquetaDeFuenteDiceQueArchivoSeUso() throws Exception {
        cuandoActivo(libroConDosHojas());

        assertTrue(new ExcelActivoConector(excel).etiquetaFuente()
                .contains("observatorio.xlsx"), "la respuesta debe poder atribuir su archivo");
    }

    /**
     * La regla anti-invencion depende de esto: si una columna se recorta, el modelo concludes
     * que no existe y responde "el archivo no tiene fecha", que es justamente inventar.
     */
    @Test
    void elInventarioDeColumnasNuncaSeRecortaAunqueHayaMuchasFilas() throws Exception {
        Workbook wb = new XSSFWorkbook();
        // Varias hojas anchas: es lo que desborda el presupuesto de contexto.
        for (int n = 1; n <= 10; n++) {
            Sheet s = wb.createSheet("Hoja" + n);
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("codigo_" + n);
            h.createCell(1).setCellValue("anio_" + n);
            for (int i = 1; i <= 500; i++) {
                Row r = s.createRow(i);
                r.createCell(0).setCellValue("V" + n + "-" + i);
                r.createCell(1).setCellValue("contenido muy largo ".repeat(40) + i);
            }
        }

        cuandoActivo(wb);
        String ctx = new ExcelActivoConector(excel).contexto();

        assertTrue(ctx.length() <= 12000, "el contexto debe respectar el tope: " + ctx.length());
        for (int n = 1; n <= 10; n++) {
            assertTrue(ctx.contains("codigo_" + n),
                    "la columna de la hoja " + n + " debe llegar aunque se recorten filas");
            assertTrue(ctx.contains("anio_" + n), "la columna de la hoja " + n + " debe llegar");
        }
        assertTrue(ctx.contains("inventario de hojas y columnas esta completo"),
                "debe avisar que lo que se recorta son filas, no columnas: " + ctx);
    }

    @Test
    void siUnaHojaTieneMasColumnasDeLasQueCabenLoDiceEnLugarDeCallar() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Row h = wb.createSheet("Proyectos").createRow(0);
        for (int i = 0; i < 40; i++) {
            h.createCell(i).setCellValue("columna_" + i);
        }

        cuandoActivo(wb);
        String ctx = new ExcelActivoConector(excel).contexto();

        assertTrue(ctx.contains("primeras 25 de 40"),
                "el modelo debe saber cuantas columnas no vio, no creer que no existen: " + ctx);
    }

    private void cuandoActivo(Workbook wb) throws IOException {
        ExcelCargadoItem item = new ExcelCargadoItem("id1", "observatorio.xlsx", 1234,
                "ACTIVO", "2026-09-27 10:00", "", "admin@ucundinamarca.edu.co", true, 0, 0, 0, true);
        when(excel.activo()).thenReturn(Optional.of(item));
        when(excel.contenido(eq("id1")))
                .thenReturn(new ReporteArchivo(bytes(wb), "observatorio.xlsx"));
    }

    private static Workbook libroConDosHojas() {
        Workbook wb = new XSSFWorkbook();
        Row hp = wb.createSheet("Proyectos").createRow(0);
        hp.createCell(0).setCellValue("codigo_proyecto");
        hp.createCell(1).setCellValue("nombre_proyecto");
        Row p1 = wb.getSheet("Proyectos").createRow(1);
        p1.createCell(0).setCellValue("PRY-001");
        p1.createCell(1).setCellValue("Biotecnologia aplicada");
        Row p2 = wb.getSheet("Proyectos").createRow(2);
        p2.createCell(0).setCellValue("PRY-002");
        p2.createCell(1).setCellValue("Energia solar");

        Row hg = wb.createSheet("Grupos").createRow(0);
        hg.createCell(0).setCellValue("id_grupo");
        hg.createCell(1).setCellValue("nombre_grupo");
        Row g1 = wb.getSheet("Grupos").createRow(1);
        g1.createCell(0).setCellValue("G01");
        g1.createCell(1).setCellValue("Grupo de Biotecnologia");
        return wb;
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
