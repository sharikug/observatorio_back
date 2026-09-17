package com.laboratory.auth.observatorio.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportadorServiceTest {

    @Test
    void leeEncabezadosYOmiteFilasVacias() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Proyectos");
            Row h = sh.createRow(0);
            String[] headers = {"id_proyecto", "codigo_proyecto", "nombre_proyecto", "anio", "periodo"};
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue("PROY001");
            r1.createCell(1).setCellValue("1");
            r1.createCell(2).setCellValue("Proyecto uno");
            r1.createCell(3).setCellValue(2021);
            r1.createCell(4).setCellValue(1);
            sh.createRow(2);
            Row r3 = sh.createRow(3);
            r3.createCell(0).setCellValue("PROY002");
            r3.createCell(1).setCellValue("2");
            r3.createCell(2).setCellValue("Proyecto dos");
            r3.createCell(3).setCellValue(2022);
            r3.createCell(4).setCellValue(2);

            ImportadorService servicio = new ImportadorService(null);
            List<ImportadorService.Fila> filas = servicio.leer(sh, new DataFormatter());

            assertEquals(2, filas.size());
            assertEquals("PROY001", filas.get(0).s("id_proyecto"));
            assertEquals("1", filas.get(0).s("codigo_proyecto"));
            assertEquals(2, filas.get(0).numero);
            assertEquals("Proyecto dos", filas.get(1).s("nombre_proyecto"));
            assertEquals(4, filas.get(1).numero);
        }
    }
}
