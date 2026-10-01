package com.laboratory.auth.observatorio.ia.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HU-07: convierte el borrador redactado por el asistente en un archivo descargable
 * (PDF o Word) con la leyenda de contenido generado por IA y el bloque de fuentes.
 *
 * <p>El borrador llega en Markdown, pero solo se interpreta el subconjunto que el
 * propio asistente produce: encabezados, vinetas, parrafos y tablas. Se evita
 * anadir un motor de Markdown completo por una decena de reglas.
 */
@Service
public class BorradorExportService {

    private static final Color VERDE = new Color(0, 72, 43);
    private static final Color GRIS = new Color(77, 77, 77);

    public byte[] pdf(String titulo, String borrador, List<String> fuentes, String leyenda) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph encabezado = new Paragraph(texto(titulo, "Informe generado por el Observatorio IA"),
                    new Font(Font.HELVETICA, 17, Font.BOLD, VERDE));
            encabezado.setAlignment(Element.ALIGN_CENTER);
            doc.add(encabezado);

            Paragraph fecha = new Paragraph("Fecha de generacion: " + LocalDate.now(),
                    new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS));
            fecha.setAlignment(Element.ALIGN_CENTER);
            fecha.setSpacingAfter(14);
            doc.add(fecha);

            // La leyenda va en la primera pagina y en su propia caja: es el aviso de que
            // el documento necesita validacion humana antes de circular.
            PdfPCell aviso = new PdfPCell(new Phrase(texto(leyenda,
                    "Contenido generado por IA. Verifique las fuentes citadas antes de su uso oficial."),
                    new Font(Font.HELVETICA, 9, Font.ITALIC, VERDE)));
            aviso.setBackgroundColor(new Color(240, 245, 242));
            aviso.setPadding(8);
            PdfPTable caja = new PdfPTable(1);
            caja.addCell(aviso);
            doc.add(caja);
            doc.add(Chunk.NEWLINE);

            for (Bloque bloque : bloques(borrador)) {
                escribir(doc, bloque);
            }
            fuentes(doc, fuentes);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del informe", e);
        }
    }

    public byte[] docx(String titulo, String borrador, List<String> fuentes, String leyenda) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph encabezado = doc.createParagraph();
            encabezado.setAlignment(ParagraphAlignment.CENTER);
            run(encabezado, texto(titulo, "Informe generado por el Observatorio IA"), 17, true);

            XWPFParagraph fecha = doc.createParagraph();
            fecha.setAlignment(ParagraphAlignment.CENTER);
            run(fecha, "Fecha de generacion: " + LocalDate.now(), 9, false);

            XWPFParagraph aviso = doc.createParagraph();
            run(aviso, texto(leyenda,
                    "Contenido generado por IA. Verifique las fuentes citadas antes de su uso oficial."),
                    9, true);

            for (Bloque bloque : bloques(borrador)) {
                escribir(doc, bloque);
            }
            if (fuentes != null && !fuentes.isEmpty()) {
                seccion(doc, "Fuentes");
                int i = 1;
                for (String fuente : fuentes) {
                    run(doc.createParagraph(), "[" + (i++) + "] " + fuente, 10, false);
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el documento de Word", e);
        }
    }

    private void escribir(Document doc, Bloque bloque) throws DocumentException {
        switch (bloque.tipo()) {
            case TITULO -> {
                Paragraph p = new Paragraph(texto(bloque.texto()),
                        new Font(Font.HELVETICA, 14, Font.BOLD, VERDE));
                p.setSpacingBefore(10);
                p.setSpacingAfter(5);
                doc.add(p);
            }
            case SECCION -> {
                Paragraph p = new Paragraph(texto(bloque.texto()),
                        new Font(Font.HELVETICA, 13, Font.BOLD, VERDE));
                p.setSpacingBefore(10);
                p.setSpacingAfter(5);
                doc.add(p);
            }
            case SUBSECCION -> {
                Paragraph p = new Paragraph(texto(bloque.texto()),
                        new Font(Font.HELVETICA, 11, Font.BOLD, GRIS));
                p.setSpacingBefore(8);
                p.setSpacingAfter(4);
                doc.add(p);
            }
            case VIÑETA -> doc.add(new Paragraph("- " + texto(bloque.texto()), cuerpo(10)));
            case PARRAFO -> doc.add(new Paragraph(texto(bloque.texto()), cuerpo(10)));
            case TABLA -> doc.add(tabla(bloque));
        }
    }

    private void escribir(XWPFDocument doc, Bloque bloque) {
        switch (bloque.tipo()) {
            case TITULO -> {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingBefore(200);
                run(p, texto(bloque.texto()), 14, true);
            }
            case SECCION -> {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingBefore(200);
                run(p, texto(bloque.texto()), 13, true);
            }
            case SUBSECCION -> {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingBefore(160);
                run(p, texto(bloque.texto()), 11, true);
            }
            case VIÑETA -> run(doc.createParagraph(), "- " + texto(bloque.texto()), 10, false);
            case PARRAFO -> run(doc.createParagraph(), texto(bloque.texto()), 10, false);
            case TABLA -> tabla(doc, bloque);
        }
    }

    private void fuentes(Document doc, List<String> fuentes) throws DocumentException {
        if (fuentes == null || fuentes.isEmpty()) {
            return;
        }
        doc.add(Chunk.NEWLINE);
        Paragraph p = new Paragraph("Fuentes", new Font(Font.HELVETICA, 13, Font.BOLD, VERDE));
        p.setSpacingBefore(10);
        doc.add(p);
        int i = 1;
        for (String fuente : fuentes) {
            doc.add(new Paragraph("[" + (i++) + "] " + fuente, cuerpo(9)));
        }
    }

    private void seccion(XWPFDocument doc, String titulo) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        run(p, titulo, 13, true);
    }

    private PdfPTable tabla(Bloque bloque) {
        List<List<String>> filas = bloque.filas();
        int columnas = filas.stream().mapToInt(List::size).max().orElse(1);
        PdfPTable tabla = new PdfPTable(columnas);
        tabla.setWidthPercentage(100);
        Font celda = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS);
        for (int f = 0; f < filas.size(); f++) {
            for (int c = 0; c < columnas; c++) {
                String valor = c < filas.get(f).size() ? filas.get(f).get(c) : "";
                PdfPCell celdaPdf = new PdfPCell(new Phrase(valor,
                        f == 0 ? new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE) : celda));
                celdaPdf.setPadding(4);
                if (f == 0) {
                    celdaPdf.setBackgroundColor(VERDE);
                }
                tabla.addCell(celdaPdf);
            }
        }
        return tabla;
    }

    private void tabla(XWPFDocument doc, Bloque bloque) {
        List<List<String>> filas = bloque.filas();
        int columnas = filas.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable tabla = doc.createTable(filas.size(), columnas);
        for (int f = 0; f < filas.size(); f++) {
            XWPFTableRow fila = tabla.getRow(f);
            for (int c = 0; c < columnas; c++) {
                String valor = c < filas.get(f).size() ? filas.get(f).get(c) : "";
                XWPFTableCell celda = fila.getCell(c);
                celda.setText(valor);
                if (f == 0) {
                    celda.getParagraphs().get(0).getRuns().forEach(r -> r.setBold(true));
                }
            }
        }
    }

    private void run(XWPFParagraph p, String texto, int tamano, boolean bold) {
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setFontFamily("Calibri");
        r.setFontSize(tamano);
        r.setBold(bold);
    }

    private Font cuerpo(int tamano) {
        return new Font(Font.HELVETICA, tamano, Font.NORMAL, GRIS);
    }

    private String texto(String valor, String alterno) {
        return valor == null || valor.isBlank() ? alterno : valor;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    // --- Lectura del Markdown -----------------------------------------------------

    private enum Tipo { TITULO, SECCION, SUBSECCION, PARRAFO, VIÑETA, TABLA }

    private record Bloque(Tipo tipo, String texto, List<List<String>> filas) {
        static Bloque de(Tipo tipo, String texto) {
            return new Bloque(tipo, texto, List.of());
        }

        static Bloque tabla(List<List<String>> filas) {
            return new Bloque(Tipo.TABLA, "", filas);
        }
    }

    List<Bloque> bloques(String markdown) {
        List<Bloque> out = new ArrayList<>();
        List<String> parrafo = new ArrayList<>();
        List<List<String>> tabla = new ArrayList<>();
        for (String cruda : (markdown == null ? "" : markdown).split("\n")) {
            String linea = cruda.replace("\r", "").trim();
            if (linea.startsWith("|")) {
                List<String> celdas = celdas(linea);
                if (!separador(celdas)) {
                    tabla.add(celdas);
                }
                continue;
            }
            cerrarTabla(tabla, out);
            if (linea.isBlank()) {
                cerrarParrafo(parrafo, out);
            } else if (linea.startsWith("### ")) {
                cerrarParrafo(parrafo, out);
                out.add(Bloque.de(Tipo.SUBSECCION, linea.substring(4)));
            } else if (linea.startsWith("## ")) {
                cerrarParrafo(parrafo, out);
                out.add(Bloque.de(Tipo.SECCION, linea.substring(3)));
            } else if (linea.startsWith("# ")) {
                cerrarParrafo(parrafo, out);
                out.add(Bloque.de(Tipo.TITULO, linea.substring(2)));
            } else if (linea.matches("^[-*+]\\s+.*")) {
                cerrarParrafo(parrafo, out);
                out.add(Bloque.de(Tipo.VIÑETA, linea.replaceFirst("^[-*+]\\s+", "")));
            } else {
                parrafo.add(linea);
            }
        }
        cerrarTabla(tabla, out);
        cerrarParrafo(parrafo, out);
        return out;
    }

    private void cerrarTabla(List<List<String>> tabla, List<Bloque> out) {
        if (!tabla.isEmpty()) {
            out.add(Bloque.tabla(List.copyOf(tabla)));
            tabla.clear();
        }
    }

    private void cerrarParrafo(List<String> parrafo, List<Bloque> out) {
        if (!parrafo.isEmpty()) {
            out.add(Bloque.de(Tipo.PARRAFO, String.join(" ", parrafo)));
            parrafo.clear();
        }
    }

    private List<String> celdas(String linea) {
        return Arrays.stream(linea.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|", -1))
                .map(this::limpiar)
                .toList();
    }

    private boolean separador(List<String> celdas) {
        return !celdas.isEmpty() && celdas.stream().allMatch(c -> c.matches("^:?-{2,}:?$"));
    }

    /** Quita el marcado en linea; no se interpreta, solo se evita que salga como asteriscos. */
    private String limpiar(String texto) {
        return texto.trim().replace("**", "").replace("`", "");
    }
}
