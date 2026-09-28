package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.Fragmento;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HU-06: extrae texto de PDF, Word (.docx) y Excel (.xlsx) y lo divide en
 * fragmentos indexables. La referencia conserva pagina/hoja/seccion de origen.
 */
@Service
public class IngestaService {

    private static final int TAMANO_FRAGMENTO = 1200;
    private static final int SOLAPE = 200;

    public List<Fragmento> extraer(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        String ext = nombre.contains(".")
                ? nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        try {
            return switch (ext) {
                case "pdf" -> pdf(archivo);
                case "docx" -> docx(archivo);
                case "xlsx", "xls" -> excel(archivo);
                case "txt", "md", "csv" -> texto(archivo);
                default -> throw new IllegalArgumentException("Formato no soportado: " + ext);
            };
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage(), e);
        }
    }

    private List<Fragmento> pdf(MultipartFile archivo) throws IOException {
        List<Fragmento> out = new ArrayList<>();
        try (InputStream in = archivo.getInputStream();
             PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pagina = 1; pagina <= doc.getNumberOfPages(); pagina++) {
                stripper.setStartPage(pagina);
                stripper.setEndPage(pagina);
                String texto = stripper.getText(doc);
                out.addAll(fragmentar(texto, "pagina " + pagina));
            }
        }
        return out;
    }

    private List<Fragmento> docx(MultipartFile archivo) throws IOException {
        List<Fragmento> out = new ArrayList<>();
        try (InputStream in = archivo.getInputStream(); XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            for (XWPFTable tabla : doc.getTables()) {
                for (XWPFTableRow fila : tabla.getRows()) {
                    List<String> celdas = new ArrayList<>();
                    for (XWPFTableCell celda : fila.getTableCells()) {
                        celdas.add(celda.getText());
                    }
                    sb.append(String.join(" | ", celdas)).append('\n');
                }
            }
            out.addAll(fragmentar(sb.toString(), "seccion"));
        }
        return out;
    }

    private List<Fragmento> excel(MultipartFile archivo) throws IOException {
        List<Fragmento> out = new ArrayList<>();
        try (InputStream in = archivo.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter fmt = new DataFormatter();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet hoja = wb.getSheetAt(s);
                StringBuilder sb = new StringBuilder();
                for (Row fila : hoja) {
                    List<String> celdas = new ArrayList<>();
                    for (Cell celda : fila) {
                        celdas.add(fmt.formatCellValue(celda));
                    }
                    sb.append(String.join(" | ", celdas)).append('\n');
                }
                out.addAll(fragmentar(sb.toString(), "hoja " + hoja.getSheetName()));
            }
        }
        return out;
    }

    private List<Fragmento> texto(MultipartFile archivo) throws IOException {
        String contenido = new String(archivo.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return fragmentar(contenido, "documento");
    }

    public List<Fragmento> fragmentar(String texto, String referencia) {
        String limpio = texto == null ? "" : texto.replace("\r", "").trim();
        List<Fragmento> out = new ArrayList<>();
        if (limpio.isBlank()) {
            return out;
        }
        int inicio = 0;
        while (inicio < limpio.length()) {
            int fin = Math.min(inicio + TAMANO_FRAGMENTO, limpio.length());
            out.add(new Fragmento(limpio.substring(inicio, fin).trim(), referencia));
            if (fin == limpio.length()) {
                break;
            }
            inicio = fin - SOLAPE;
        }
        return out;
    }
}
