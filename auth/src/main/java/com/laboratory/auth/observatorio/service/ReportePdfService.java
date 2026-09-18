package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.ReporteDocumento;
import com.laboratory.auth.observatorio.api.dto.ReporteGrafico;
import com.laboratory.auth.observatorio.api.dto.ReporteIndicador;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
public class ReportePdfService {

    private static final Color VERDE = new Color(0, 72, 43);
    private static final Color VERDE_MEDIO = new Color(0, 123, 62);
    private static final Color GRIS = new Color(77, 77, 77);
    private static final Color FONDO = new Color(240, 245, 242);
    private static final String SIN_DATOS = "No se registran datos";
    private static final int MAX_FILAS = 500; // ponytail: tope para PDFs legibles, paginar si se necesita todo

    public byte[] generar(ReporteDocumento documento) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font meta = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);

            Paragraph encabezado = new Paragraph("Informe institucional",
                    new Font(Font.HELVETICA, 18, Font.BOLD, VERDE));
            encabezado.setAlignment(Element.ALIGN_CENTER);
            doc.add(encabezado);

            Paragraph subtitulo = new Paragraph(
                    texto(documento.tablero(), "Observatorio de Investigacion"),
                    new Font(Font.HELVETICA, 12, Font.NORMAL, GRIS));
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(subtitulo);

            Paragraph fecha = new Paragraph("Fecha de generacion: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), meta);
            fecha.setAlignment(Element.ALIGN_CENTER);
            fecha.setSpacingAfter(18);
            doc.add(fecha);

            seccion(doc, "Filtros aplicados");
            List<String> filtros = documento.filtrosAplicados();
            if (filtros == null || filtros.isEmpty()) {
                doc.add(new Paragraph("Sin filtros: todos los registros disponibles", texto()));
            } else {
                for (String filtro : filtros) {
                    doc.add(new Paragraph("- " + filtro, texto()));
                }
            }
            doc.add(Chunk.NEWLINE);

            seccion(doc, "Indicadores principales");
            doc.add(tablaIndicadores(documento.indicadores()));
            doc.add(Chunk.NEWLINE);

            seccion(doc, "Graficas y diagramas");
            doc.add(graficos(documento.graficos()));
            doc.add(Chunk.NEWLINE);

            seccion(doc, "Detalle");
            doc.add(tablaDetalle(documento.columnas(), documento.filas()));

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Fuente: " + texto(documento.fuente(),
                    "Observatorio de Investigacion - Sistema de informacion institucional"), meta));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte", e);
        }
    }

    private void seccion(Document doc, String texto) {
        Paragraph p = new Paragraph(texto, new Font(Font.HELVETICA, 13, Font.BOLD, VERDE_MEDIO));
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private Font texto() {
        return new Font(Font.HELVETICA, 10, Font.NORMAL, GRIS);
    }

    private PdfPTable tablaIndicadores(List<ReporteIndicador> indicadores) throws DocumentException {
        if (indicadores == null || indicadores.isEmpty()) {
            return celdaUnica(SIN_DATOS);
        }
        PdfPTable tabla = new PdfPTable(new float[]{3f, 1f});
        tabla.setWidthPercentage(70);
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
        for (ReporteIndicador indicador : indicadores) {
            PdfPCell etiqueta = new PdfPCell(new Phrase(indicador.etiqueta(),
                    new Font(Font.HELVETICA, 10, Font.BOLD, GRIS)));
            etiqueta.setPadding(6);
            etiqueta.setBackgroundColor(FONDO);
            tabla.addCell(etiqueta);

            PdfPCell valor = new PdfPCell(new Phrase(indicador.valor(),
                    new Font(Font.HELVETICA, 11, Font.BOLD, VERDE)));
            valor.setPadding(6);
            tabla.addCell(valor);
        }
        return tabla;
    }

    private PdfPTable graficos(List<ReporteGrafico> graficos) throws DocumentException {
        if (graficos == null || graficos.isEmpty()) {
            return celdaUnica(SIN_DATOS);
        }
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        for (ReporteGrafico grafico : graficos) {
            PdfPCell celda = new PdfPCell();
            celda.setBorder(0);
            celda.setPadding(4);
            celda.addElement(new Paragraph(texto(grafico.titulo(), "Grafica"),
                    new Font(Font.HELVETICA, 10, Font.BOLD, VERDE)));
            try {
                byte[] bytes = Base64.getDecoder().decode(grafico.imagen());
                Image imagen = Image.getInstance(bytes);
                imagen.scaleToFit(500, 340);
                celda.addElement(imagen);
            } catch (Exception e) {
                celda.addElement(new Paragraph(SIN_DATOS, texto()));
            }
            tabla.addCell(celda);
        }
        return tabla;
    }

    private PdfPTable tablaDetalle(List<String> columnas, List<List<String>> filas) throws DocumentException {
        if (columnas == null || columnas.isEmpty() || filas == null || filas.isEmpty()) {
            return celdaUnica(SIN_DATOS);
        }
        PdfPTable tabla = new PdfPTable(columnas.size());
        tabla.setWidthPercentage(100);
        for (String columna : columnas) {
            PdfPCell celda = new PdfPCell(new Phrase(columna,
                    new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE)));
            celda.setBackgroundColor(VERDE_MEDIO);
            celda.setPadding(4);
            tabla.addCell(celda);
        }

        Font fuente = new Font(Font.HELVETICA, 7, Font.NORMAL, GRIS);
        int limite = Math.min(filas.size(), MAX_FILAS);
        for (int i = 0; i < limite; i++) {
            List<String> fila = filas.get(i);
            for (int j = 0; j < columnas.size(); j++) {
                String valor = fila != null && j < fila.size() && fila.get(j) != null ? fila.get(j) : "";
                PdfPCell celda = new PdfPCell(new Phrase(valor, fuente));
                celda.setPadding(3);
                tabla.addCell(celda);
            }
        }
        if (filas.size() > limite) {
            PdfPCell nota = new PdfPCell(new Phrase(
                    "Se muestran los primeros " + limite + " de " + filas.size() + " registros.", fuente));
            nota.setColspan(columnas.size());
            nota.setPadding(4);
            tabla.addCell(nota);
        }
        return tabla;
    }

    private PdfPTable celdaUnica(String texto) throws DocumentException {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.addCell(new PdfPCell(new Phrase(texto, texto())));
        return tabla;
    }

    private String texto(String valor, String alterno) {
        return valor == null || valor.isBlank() ? alterno : valor;
    }
}
