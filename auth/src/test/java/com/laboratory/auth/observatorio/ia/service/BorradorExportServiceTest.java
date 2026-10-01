package com.laboratory.auth.observatorio.ia.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-07: el informe descargable es un archivo real con leyenda y fuentes, no un
 * HTML disfrazado. Estas pruebas fijan la maquina de assinaturas de cada formato.
 */
class BorradorExportServiceTest {

    private static final String BORRADOR = """
            ## Resumen ejecutivo

            El portafolio agrupa 49 grupos de investigacion.

            - Distribucion por facultad
            - Produccion por ano

            ## Produccion por facultad

            | Facultad | Proyectos |
            | --- | ---: |
            | Facultad A | 120 |
            | Facultad B | 98 |
            """;

    private final BorradorExportService servicio = new BorradorExportService();

    @Test
    void generaUnPdfReal() {
        byte[] pdf = servicio.pdf("Produccion cientifica", BORRADOR, List.of("observatorio.xlsx"), "Leyenda IA");

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(pdf.length > 1000, "el PDF debe traer contenido, no una pagina vacia");
    }

    @Test
    void generaUnWordReal() {
        byte[] docx = servicio.docx("Produccion cientifica", BORRADOR, List.of("observatorio.xlsx"), "Leyenda IA");

        // Los .docx son un zip: "PK" al inicio y la palabra "word/" dentro.
        assertEquals("PK", new String(docx, 0, 2, StandardCharsets.US_ASCII));
        assertTrue(new String(docx, StandardCharsets.ISO_8859_1).contains("word/document.xml"));
    }

    @Test
    void unBorradorVacioNoRompeLaExportacion() {
        assertEquals("%PDF", new String(servicio.pdf("Informe", "", List.of(), ""), 0, 4,
                StandardCharsets.US_ASCII));
        assertEquals("PK", new String(servicio.docx("Informe", null, null, null), 0, 2,
                StandardCharsets.US_ASCII));
    }

    /**
     * El prompt del asistente produce Markdown; el archivo final debe traer la estructura
     * convertida y sin marcas visibles. Se lee el .docx generado en vez de inspeccionar
     * la clase interna: lo que importa es lo que el usuario abre.
     */
    @Test
    void elMarkdownLlegaConvertidoAlDocumentoYSinAsteriscos() throws Exception {
        String xml = textoDocumento(servicio.docx("Produccion cientifica", BORRADOR,
                List.of("observatorio.xlsx"), "Leyenda IA"));

        assertTrue(xml.contains("Resumen ejecutivo"), xml);
        assertTrue(xml.contains("Produccion por facultad"), xml);
        assertTrue(xml.contains("Distribucion por facultad"), xml);
        // La tabla llega como tabla de Word, con sus dos filas de datos y sin la fila "---".
        assertTrue(xml.contains("Facultad") && xml.contains("120") && xml.contains("98"), xml);
        assertTrue(xml.contains("Leyenda IA") && xml.contains("observatorio.xlsx"), xml);
        assertTrue(!xml.contains("**") && !xml.contains("|"), "no deben quedar marcas de markdown: " + xml);
        assertTrue(!xml.contains("---"), "la fila separadora de la tabla no es un dato: " + xml);
    }

    /** Extrae word/document.xml del .docx (que es un zip) y lo pasa a texto plano. */
    private String textoDocumento(byte[] docx) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (entrada.getName().equals("word/document.xml")) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
                }
            }
        }
        throw new AssertionError("el .docx no contiene word/document.xml");
    }
}
