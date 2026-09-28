package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.ImportarError;
import com.laboratory.auth.observatorio.api.dto.ImportarResumen;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImportadorService {

    private static final String IMPORTADO = "IMPORTADO";
    private static final String MANUAL = "MANUAL";

    private final JdbcTemplate jdbc;

    public ImportarResumen importar(MultipartFile file) {
        Contador contador = new Contador();
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter fmt = new DataFormatter();
            for (ReglasImportacion.Regla regla : ReglasImportacion.HOJAS) {
                if (regla.controlManual()) {
                    importarProyectos(wb, fmt, regla, contador);
                } else {
                    importarTabla(wb, fmt, regla, contador);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo Excel: " + e.getMessage());
        }
        return new ImportarResumen(contador.creados, contador.actualizados, contador.omitidos,
                List.copyOf(contador.errores));
    }

    /**
     * Purga los datos que trajo el Excel anterior para que dashboards e IA no mezclen
     * archivos. Se ejecuta justo antes de importar el nuevo Excel activo.
     *
     * <p>En las tablas con columna 'origen' solo se borra lo IMPORTADO: los proyectos
     * corregidos a mano se preservan, que es lo que protege la regla controlManual.
     * En el resto de tablas no existe esa marca, asi que se vacian por completo.
     */
    public void limpiar() {
        for (ReglasImportacion.Regla regla : ReglasImportacion.HOJAS) {
            if (regla.tieneOrigen()) {
                jdbc.update("DELETE FROM " + regla.tabla() + " WHERE origen <> 'MANUAL'");
            } else {
                jdbc.update("DELETE FROM " + regla.tabla());
            }
        }
    }

    private void importarProyectos(Workbook wb, DataFormatter fmt,
                                   ReglasImportacion.Regla regla, Contador contador) {
        importarHoja(wb, fmt, regla, contador, f -> {
            validarClaves(f, regla);
            String codigo = f.s("codigo_proyecto");
            if (MANUAL.equalsIgnoreCase(origenExistente(codigo))) {
                contador.omitidos++;
                contador.errores.add(new ImportarError(regla.hoja(), f.numero, "codigo_proyecto",
                        "Registro con correccion manual no sobrescrito"));
                return;
            }
            LinkedHashMap<String, Object> cols = columnas(f, regla);
            cols.put("origen", IMPORTADO);
            upsert(regla.tabla(), regla.claves(), cols, contador);
        });
    }

    private void importarTabla(Workbook wb, DataFormatter fmt,
                               ReglasImportacion.Regla regla, Contador contador) {
        importarHoja(wb, fmt, regla, contador, f -> {
            validarClaves(f, regla);
            upsert(regla.tabla(), regla.claves(), columnas(f, regla), contador);
        });
    }

    private void importarHoja(Workbook wb, DataFormatter fmt, ReglasImportacion.Regla regla,
                              Contador contador, FilaHandler handler) {
        Sheet sh = wb.getSheet(regla.hoja());
        if (sh == null) {
            contador.errores.add(new ImportarError(regla.hoja(), 0, "", "La hoja no existe en el archivo"));
            return;
        }
        for (Fila f : leer(sh, fmt)) {
            try {
                handler.handle(f);
            } catch (DatoInvalido e) {
                contador.omitidos++;
                contador.errores.add(new ImportarError(regla.hoja(), f.numero, e.campo, e.getMessage()));
            } catch (RuntimeException e) {
                contador.omitidos++;
                contador.errores.add(new ImportarError(regla.hoja(), f.numero, "", e.getMessage()));
            }
        }
    }

    private void upsert(String tabla, List<String> claves, LinkedHashMap<String, Object> cols,
                        Contador contador) {
        String where = String.join(" AND ", claves.stream().map(c -> c + " = ?").toList());
        Object[] valoresClave = claves.stream().map(cols::get).toArray();
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM " + tabla + " WHERE " + where, Integer.class, valoresClave);
        boolean existe = n != null && n > 0;

        String columnas = String.join(", ", cols.keySet());
        String marcadores = String.join(", ", cols.keySet().stream().map(c -> "?").toList());
        List<String> updates = cols.keySet().stream()
                .filter(c -> !claves.contains(c))
                .map(c -> c + " = EXCLUDED." + c)
                .toList();
        String conflicto = updates.isEmpty()
                ? " ON CONFLICT (" + String.join(", ", claves) + ") DO NOTHING"
                : " ON CONFLICT (" + String.join(", ", claves) + ") DO UPDATE SET " + String.join(", ", updates);

        jdbc.update("INSERT INTO " + tabla + " (" + columnas + ") VALUES (" + marcadores + ")" + conflicto,
                cols.values().toArray());
        if (existe) {
            contador.actualizados++;
        } else {
            contador.creados++;
        }
    }

    private String origenExistente(String codigo) {
        return jdbc.query("SELECT origen FROM proyecto WHERE codigo_proyecto = ?",
                rs -> rs.next() ? rs.getString(1) : null, codigo);
    }

    List<Fila> leer(Sheet sh, DataFormatter fmt) {
        List<Fila> filas = new ArrayList<>();
        Iterator<Row> it = sh.iterator();
        if (!it.hasNext()) {
            return filas;
        }
        Row head = it.next();
        List<String> headers = new ArrayList<>();
        for (Cell c : head) {
            headers.add(fmt.formatCellValue(c).trim());
        }
        while (it.hasNext()) {
            Row r = it.next();
            LinkedHashMap<String, String> cols = new LinkedHashMap<>();
            boolean vacia = true;
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (header.isEmpty()) {
                    continue;
                }
                Cell c = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String valor = c == null ? "" : fmt.formatCellValue(c).trim();
                cols.put(header, valor);
                if (!valor.isEmpty()) {
                    vacia = false;
                }
            }
            if (!vacia) {
                filas.add(new Fila(cols, r.getRowNum() + 1));
            }
        }
        return filas;
    }

    private LinkedHashMap<String, Object> columnas(Fila f, ReglasImportacion.Regla regla) {
        LinkedHashMap<String, Object> cols = new LinkedHashMap<>();
        for (String h : f.cols.keySet()) {
            cols.put(h, valor(f, h, regla));
        }
        return cols;
    }

    private Object valor(Fila f, String col, ReglasImportacion.Regla regla) {
        String v = f.s(col);
        if (v.isEmpty()) {
            return null;
        }
        if (regla.numericas().contains(col)) {
            try {
                return (int) Math.round(Double.parseDouble(v.replace(",", ".")));
            } catch (NumberFormatException e) {
                throw new DatoInvalido(col, "valor no numerico: " + v);
            }
        }
        return v;
    }

    private void validarClaves(Fila f, ReglasImportacion.Regla regla) {
        for (String k : regla.claves()) {
            if (f.s(k).isEmpty()) {
                throw new DatoInvalido(k, "campo obligatorio vacio");
            }
        }
    }

    /** Estado de una importacion. Antes era campo del servicio, y dos cargas simultaneas se pisaban. */
    private static class Contador {
        private final List<ImportarError> errores = new ArrayList<>();
        private int creados;
        private int actualizados;
        private int omitidos;
    }

    public static class Fila {
        private final Map<String, String> cols;
        public final int numero;

        Fila(Map<String, String> cols, int numero) {
            this.cols = cols;
            this.numero = numero;
        }

        public String s(String header) {
            return cols.getOrDefault(header, "");
        }
    }

    private static class DatoInvalido extends RuntimeException {
        private final String campo;

        DatoInvalido(String campo, String mensaje) {
            super(mensaje);
            this.campo = campo;
        }
    }

    @FunctionalInterface
    private interface FilaHandler {
        void handle(Fila f);
    }
}
