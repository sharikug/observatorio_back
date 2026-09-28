package com.laboratory.auth.observatorio.ia.conector;

import com.laboratory.auth.observatorio.api.dto.ExcelCargadoItem;
import com.laboratory.auth.observatorio.service.ExcelActivoService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fuente de verdad del asistente: el Excel ACTIVO.
 *
 * <p>Entrega la estructura real del archivo (hojas, columnas y una muestra de filas)
 * leida del .xlsx guardado, para que el modelo sepa que datos existen y de que hoja
 * salen. Nunca lee archivos historicos: solo el que esta marcado como ACTIVO.
 *
 * <p>Las preguntas de agregacion no se resuelven aqui sino con SQL sobre las tablas,
 * que en ese momento reflejan exactamente este mismo archivo.
 */
@Component
@RequiredArgsConstructor
public class ExcelActivoConector {

    /** Tope de filas de muestra por hoja: un .xlsx de 20MB no cabe en un prompt. */
    private static final int FILAS_MUESTRA = 8;
    private static final int MAX_COLUMNAS = 25;
    private static final int MAX_TEXTO = 12000;

    private final ExcelActivoService excelActivoService;

    public boolean hayActivo() {
        return excelActivoService.activo().isPresent();
    }

    /** Descripcion corta de la fuente, para mostrarla junto a la respuesta. */
    public String etiquetaFuente() {
        return excelActivoService.activo()
                .map(a -> a.nombre() + " (cargado el " + a.fechaCarga() + ")")
                .orElse("sin Excel activo");
    }

    /**
     * Estructura del Excel activo lista para el prompt: por hoja, las columnas y una
     * muestra de registros, sin mezclar hojas entre si. Blank si no hay Excel activo.
     */
    public String contexto() {
        ExcelCargadoItem activo = excelActivoService.activo().orElse(null);
        if (activo == null) {
            return "";
        }
        byte[] bytes = excelActivoService.contenido(activo.id()).contenido();
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ARCHIVO FUENTE ACTIVO: ").append(activo.nombre())
                .append(" | cargado el ").append(activo.fechaCarga())
                .append(" | usuario ").append(activo.usuario())
                .append(" | ").append(activo.estado()).append('\n');
        sb.append("Inventario completo del archivo: si un campo no aparece aqui, no existe en el Excel.\n");
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            // Dos pasadas. El inventario de hojas y columnas se escribe COMPLETO primero,
            // y las muestras despues, ya acotadas. Si se mezclara en una sola pasada, unas
            // filas largas agotarian el presupuesto y las hojas siguientes no aparecerian:
            // el modelo concluiria que esas columnas no existen, que es justo la invencion
            // que la regla anti-invencion debe impedir.
            List<Muestra> muestras = new ArrayList<>();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                muestras.add(inventario(sb, wb.getSheetAt(s), fmt));
            }
            for (Muestra m : muestras) {
                escribirMuestra(sb, m);
            }
        } catch (Exception e) {
            sb.append("No se pudo releer el archivo fuente: ").append(e.getMessage()).append('\n');
        }
        return recortar(sb.toString());
    }

    /** Recorta por el final, que es donde solo hay filas de muestra, nunca columnas. */
    private String recortar(String contexto) {
        if (contexto.length() <= MAX_TEXTO) {
            return contexto;
        }
        int corte = contexto.lastIndexOf('\n', MAX_TEXTO);
        return contexto.substring(0, corte < 0 ? MAX_TEXTO : corte)
                + "\n(se recortaron filas de muestra; el inventario de hojas y columnas esta completo)\n";
    }

    /** Escribe nombre, cantidad de filas y columnas de la hoja. Nunca se omite ni se recorta. */
    private Muestra inventario(StringBuilder sb, Sheet sh, DataFormatter fmt) {
        var it = sh.iterator();
        if (!it.hasNext()) {
            return new Muestra(sh.getSheetName(), List.of(), 0, false);
        }        Row head = it.next();
        int totalColumnas = Math.max(head.getLastCellNum(), 0);
        List<String> columnas = new ArrayList<>();
        for (int i = 0; i < totalColumnas && i < MAX_COLUMNAS; i++) {
            columnas.add(valor(head.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), fmt));
        }
        int filas = 0;
        List<List<String>> muestra = new ArrayList<>();
        while (it.hasNext()) {
            Row r = it.next();
            List<String> valores = new ArrayList<>();
            for (int i = 0; i < columnas.size(); i++) {
                valores.add(valor(r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), fmt));
            }
            if (valores.stream().anyMatch(v -> !v.isEmpty())) {
                filas++;
                if (muestra.size() < FILAS_MUESTRA) {
                    muestra.add(valores);
                }
            }
        }
        sb.append("\nHOJA: ").append(sh.getSheetName())
                .append(" | ").append(filas).append(" filas de datos\n");
        // Si la hoja tiene mas columnas de las que caben, se dice cuantas se omitieron:
        // el modelo debe saber que no las vio, no creer que no existen.
        sb.append("  COLUMNAS");
        if (totalColumnas > columnas.size()) {
            sb.append(" (primeras ").append(columnas.size()).append(" de ").append(totalColumnas).append(')');
        }
        sb.append(": ").append(String.join(" | ", columnas)).append('\n');
        return new Muestra(sh.getSheetName(), List.copyOf(muestra), filas, !muestra.isEmpty());
    }

    private void escribirMuestra(StringBuilder sb, Muestra m) {
        if (!m.conMuestra() || sb.length() >= MAX_TEXTO) {
            return;
        }
        sb.append("  REGISTROS de ").append(m.hoja()).append(" (muestra de ")
                .append(m.muestra().size()).append(" de ").append(m.filas()).append("):\n");
        for (List<String> fila : m.muestra()) {
            sb.append("   ").append(String.join(" | ", fila)).append('\n');
        }
    }

    private record Muestra(String hoja, List<List<String>> muestra, int filas, boolean conMuestra) {
    }

    private String valor(Cell c, DataFormatter fmt) {
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }
}
