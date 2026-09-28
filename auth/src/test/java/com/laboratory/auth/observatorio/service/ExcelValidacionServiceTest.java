package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.TipoInconsistencia;
import com.laboratory.auth.observatorio.api.dto.ValidacionInconsistencia;
import com.laboratory.auth.observatorio.api.dto.ValidacionResultado;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelValidacionServiceTest {

    private final ExcelValidacionService servicio = new ExcelValidacionService();

    @Test
    void archivoCompletoNoReportaInconsistencias() throws Exception {
        ValidacionResultado v = validar(libroValido());

        assertTrue(v.valido(), () -> "inconsistencias: " + v.inconsistencias());
        assertEquals(0, v.totalInconsistencias());
        assertTrue(v.puedeContinuar());
        assertEquals(ReglasImportacion.HOJAS.size(), v.hojas().size());
    }

    @Test
    void celdaObligatoriaVaciaSeUbicaEnLaCelda() throws Exception {
        Workbook wb = libroValido();
        encabezado(wb, "Proyectos", "codigo_proyecto", "anio");
        fila(wb, "Proyectos", "", "2021");

        ValidacionResultado v = validar(wb);
        ValidacionInconsistencia i = unica(v, "Celda obligatoria vacia");

        assertEquals("A3", i.celda());
        assertEquals("Proyectos", i.hoja());
        assertEquals("codigo_proyecto", i.nombreColumna());
        assertEquals(3, i.fila());
        assertTrue(i.sugerencia().contains("codigo_proyecto"), i.sugerencia());
        assertTrue(v.puedeContinuar(), "una celda vacia no debe bloquear la importacion");
    }

    @Test
    void tipoIncorrectoSeReportaConElValorEncontrado() throws Exception {
        Workbook wb = libroValido();
        encabezado(wb, "Proyectos", "codigo_proyecto", "anio");
        fila(wb, "Proyectos", "PROY-9", "veinticinco");

        ValidacionInconsistencia i = unica(validar(wb), "Tipo de dato incorrecto");

        assertEquals("B3", i.celda());
        assertEquals("anio", i.nombreColumna());
        assertTrue(i.mensaje().contains("veinticinco"), i.mensaje());
    }

    @Test
    void encabezadoDuplicadoIndicaLasDosColumnas() throws Exception {
        Workbook wb = libroValido();
        encabezado(wb, "Proyectos", "codigo_proyecto", "codigo_proyecto");

        ValidacionInconsistencia i = unica(validar(wb), "Encabezado duplicado");

        assertEquals("B1", i.celda());
        assertTrue(i.mensaje().contains("A y B"), i.mensaje());
    }

    @Test
    void columnaNoReconocidaEsCriticaYBloquea() throws Exception {
        Workbook wb = libroValido();
        encabezado(wb, "Proyectos", "codigo_proyecto", "facultad_inexistente");

        ValidacionResultado v = validar(wb);

        assertEquals(TipoInconsistencia.ENCABEZADO_DESCONOCIDO, v.inconsistencias().get(0).tipo());
        assertFalse(v.puedeContinuar(), "una columna inexistente impediria guardar cualquier fila");
        assertEquals(1, v.criticas());
    }

    @Test
    void faltaDeEncabezadoObligatorioEsCritica() throws Exception {
        Workbook wb = libroValido();
        encabezado(wb, "Proyectos", "nombre_proyecto");

        ValidacionResultado v = validar(wb);

        assertEquals(TipoInconsistencia.ENCABEZADO_FALTANTE, v.inconsistencias().get(0).tipo());
        assertFalse(v.puedeContinuar());
    }

    @Test
    void resumeLosProblemasPorHoja() throws Exception {
        Workbook wb = libroValido();
        encabezado(wb, "Proyectos", "codigo_proyecto", "anio");
        fila(wb, "Proyectos", "PROY-1", "veinticinco");
        encabezado(wb, "Proyecto_Equipo", "id_proyecto", "tamano_equipo");
        fila(wb, "Proyecto_Equipo", "PROY-1", "muchos");

        ValidacionResultado v = validar(wb);

        assertEquals(0, v.criticas(), "un mal tipo de dato es advertencia, no error critico");
        assertEquals(2, v.advertencias());
        assertEquals(List.of("Proyectos", "Proyecto_Equipo"),
                v.hojas().stream().filter(h -> h.inconsistencias() > 0).map(h -> h.nombre()).toList());
        assertTrue(v.hojas().stream().anyMatch(h -> h.nombre().equals("Grupos") && h.inconsistencias() == 0),
                "las hojas limpias tambien se informan, para saber que se revisaron");
    }

    @Test
    void detectaFilasVaciasDuplicadasYColumnasSinDatos() throws Exception {
        Workbook wb = libroValido();
        encabezado(wb, "Proyectos", "codigo_proyecto", "anio");
        fila(wb, "Proyectos", "PROY-1", "2021");
        fila(wb, "Proyectos", "PROY-1", "2022");
        filaVacia(wb, "Proyectos");
        encabezado(wb, "Grupos", "id_grupo", "nombre_grupo");
        fila(wb, "Grupos", "G-1", "");
        fila(wb, "Grupos", "G-2", "");

        ValidacionResultado v = validar(wb);

        assertTrue(tipos(v).contains("DUPLICADO"), tipos(v).toString());
        assertTrue(tipos(v).contains("FILA_VACIA"), tipos(v).toString());
        assertTrue(tipos(v).contains("COLUMNA_VACIA"), tipos(v).toString());
        assertEquals("A4", v.inconsistencias().stream()
                .filter(i -> i.tipo() == TipoInconsistencia.DUPLICADO).findFirst().orElseThrow().celda());
    }

    @Test
    void rechazaArchivoQueNoEsExcel() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "datos.txt", "text/plain",
                "a;b;c".getBytes(StandardCharsets.UTF_8));

        ValidacionResultado v = servicio.validar(archivo);

        assertEquals(TipoInconsistencia.ARCHIVO_NO_EXCEL, v.inconsistencias().get(0).tipo());
        assertFalse(v.puedeContinuar());
    }

    @Test
    void rechazaArchivoCorruptoOlegible() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "roto.xlsx", null,
                "esto no es un zip de excel".getBytes(StandardCharsets.UTF_8));

        ValidacionResultado v = servicio.validar(archivo);

        assertEquals(TipoInconsistencia.ARCHIVO_CORRUPTO, v.inconsistencias().get(0).tipo());
        assertFalse(v.puedeContinuar());
        assertTrue(v.inconsistencias().get(0).sugerencia().contains(".xlsx"));
    }

    @Test
    void informaHojasFaltantesYDesconocidasComoAdvertencia() throws Exception {
        Workbook wb = libroValido();
        wb.removeSheetAt(wb.getSheetIndex("Grupo_Programa"));
        wb.createSheet("Notas");

        ValidacionResultado v = validar(wb);

        assertTrue(tipos(v).contains("HOJA_FALTANTE"), tipos(v).toString());
        assertTrue(tipos(v).contains("HOJA_NO_RECONOCIDA"), tipos(v).toString());
        assertTrue(v.puedeContinuar(), "una hoja faltante no impide procesar el resto");
    }

    // --- utilidades -----------------------------------------------------------

    private static ValidacionInconsistencia unica(ValidacionResultado v, String etiqueta) {
        List<ValidacionInconsistencia> hits = v.inconsistencias().stream()
                .filter(i -> i.tipo().etiqueta().equals(etiqueta)).toList();
        assertEquals(1, hits.size(), () -> "esperaba 1 de '" + etiqueta + "', hubo: " + v.inconsistencias());
        return hits.get(0);
    }

    private static List<String> tipos(ValidacionResultado v) {
        return v.inconsistencias().stream().map(i -> i.tipo().name()).toList();
    }

    private static void encabezado(Workbook wb, String hoja, String... columnas) {
        Row h = wb.getSheet(hoja).getRow(0);
        for (int i = 0; i < columnas.length; i++) {
            h.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(columnas[i]);
        }
    }

    private static void fila(Workbook wb, String hoja, String... valores) {
        Sheet sh = wb.getSheet(hoja);
        Row r = sh.createRow(sh.getLastRowNum() + 1);
        for (int i = 0; i < valores.length; i++) {
            r.createCell(i).setCellValue(valores[i]);
        }
    }

    private static void filaVacia(Workbook wb, String hoja) {
        Sheet sh = wb.getSheet(hoja);
        sh.createRow(sh.getLastRowNum() + 1);
    }

    private ValidacionResultado validar(Workbook wb) throws IOException {
        return servicio.validar(new MockMultipartFile("archivo", "observatorio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes(wb)));
    }

    /** Libro con las hojas esperadas, encabezados correctos y una fila de datos valida. */
    private static Workbook libroValido() {
        Workbook wb = new XSSFWorkbook();
        for (ReglasImportacion.Regla r : ReglasImportacion.HOJAS) {
            Sheet sh = wb.createSheet(r.hoja());
            Row h = sh.createRow(0);
            Row d = sh.createRow(1);
            for (int i = 0; i < r.claves().size(); i++) {
                h.createCell(i).setCellValue(r.claves().get(i));
                d.createCell(i).setCellValue("V-" + r.hoja() + "-" + i);
            }
        }
        return wb;
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
