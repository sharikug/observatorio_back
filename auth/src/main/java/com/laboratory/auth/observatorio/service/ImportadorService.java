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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImportadorService {

    private static final String IMPORTADO = "IMPORTADO";
    private static final String MANUAL = "MANUAL";
    private static final Set<String> NUMERICAS =
            Set.of("anio", "orden_participacion", "numero_ods", "tamano_equipo");

    private final JdbcTemplate jdbc;

    private final List<ImportarError> errores = new ArrayList<>();
    private int creados;
    private int actualizados;
    private int omitidos;

    public ImportarResumen importar(MultipartFile file) {
        errores.clear();
        creados = 0;
        actualizados = 0;
        omitidos = 0;
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter fmt = new DataFormatter();
            importarTabla(wb, fmt, "Facultades", "facultad", List.of("id_facultad"));
            importarTabla(wb, fmt, "Programas", "programa", List.of("id_programa"));
            importarTabla(wb, fmt, "Unidad_Regional", "unidad_regional", List.of("id_unidad_regional"));
            importarTabla(wb, fmt, "Lineas_Translocales", "linea_translocal", List.of("id_linea"));
            importarTabla(wb, fmt, "ODS", "ods", List.of("id_ods"));
            importarTabla(wb, fmt, "Investigadores", "investigador", List.of("id_investigador"));
            importarTabla(wb, fmt, "Facultades_Grupo", "facultad_grupo", List.of("id_facultad_grupo"));
            importarTabla(wb, fmt, "Unidad_Regional_Grupo", "unidad_regional_grupo", List.of("id_unidad_regional_grupo"));
            importarTabla(wb, fmt, "Programas_Grupo", "programa_grupo", List.of("id_programa_grupo"));
            importarTabla(wb, fmt, "Grupos", "grupo", List.of("id_grupo"));
            importarProyectos(wb, fmt);
            importarTabla(wb, fmt, "Participacion", "participacion", List.of("id_participacion"));
            importarTabla(wb, fmt, "Proyecto_Equipo", "proyecto_equipo", List.of("id_proyecto"));
            importarTabla(wb, fmt, "Proyecto_Colaboracion_Grupo", "proyecto_colaboracion_grupo", List.of("id_colaboracion"));
            importarTabla(wb, fmt, "Proyecto_Linea", "proyecto_linea", List.of("id_proyecto", "id_linea"));
            importarTabla(wb, fmt, "Proyecto_ODS", "proyecto_ods", List.of("id_proyecto", "id_ods"));
            importarTabla(wb, fmt, "Proyecto_Grupo", "proyecto_grupo", List.of("id_proyecto", "id_grupo"));
            importarTabla(wb, fmt, "Grupo_Programa", "grupo_programa", List.of("id_grupo", "id_programa_grupo"));
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo Excel: " + e.getMessage());
        }
        return new ImportarResumen(creados, actualizados, omitidos, List.copyOf(errores));
    }

    private void importarProyectos(Workbook wb, DataFormatter fmt) {
        importarHoja(wb, fmt, "Proyectos", f -> {
            validarClaves(f, List.of("codigo_proyecto"));
            String codigo = f.s("codigo_proyecto");
            if (MANUAL.equalsIgnoreCase(origenExistente(codigo))) {
                omitidos++;
                errores.add(new ImportarError("Proyectos", f.numero, "codigo_proyecto",
                        "Registro con correccion manual no sobrescrito"));
                return;
            }
            LinkedHashMap<String, Object> cols = columnas(f);
            cols.put("origen", IMPORTADO);
            upsert("proyecto", List.of("codigo_proyecto"), cols);
        });
    }

    private void importarTabla(Workbook wb, DataFormatter fmt, String hoja, String tabla, List<String> claves) {
        importarHoja(wb, fmt, hoja, f -> {
            validarClaves(f, claves);
            upsert(tabla, claves, columnas(f));
        });
    }

    private void importarHoja(Workbook wb, DataFormatter fmt, String hoja, FilaHandler handler) {
        Sheet sh = wb.getSheet(hoja);
        if (sh == null) {
            errores.add(new ImportarError(hoja, 0, "", "La hoja no existe en el archivo"));
            return;
        }
        for (Fila f : leer(sh, fmt)) {
            try {
                handler.handle(f);
            } catch (DatoInvalido e) {
                omitidos++;
                errores.add(new ImportarError(hoja, f.numero, e.campo, e.getMessage()));
            } catch (RuntimeException e) {
                omitidos++;
                errores.add(new ImportarError(hoja, f.numero, "", e.getMessage()));
            }
        }
    }

    private void upsert(String tabla, List<String> claves, LinkedHashMap<String, Object> cols) {
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
            actualizados++;
        } else {
            creados++;
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

    private LinkedHashMap<String, Object> columnas(Fila f) {
        LinkedHashMap<String, Object> cols = new LinkedHashMap<>();
        for (String h : f.cols.keySet()) {
            cols.put(h, valor(f, h));
        }
        return cols;
    }

    private Object valor(Fila f, String col) {
        String v = f.s(col);
        if (v.isEmpty()) {
            return null;
        }
        if (NUMERICAS.contains(col)) {
            try {
                return (int) Math.round(Double.parseDouble(v.replace(",", ".")));
            } catch (NumberFormatException e) {
                throw new DatoInvalido(col, "valor no numerico: " + v);
            }
        }
        return v;
    }

    private void validarClaves(Fila f, List<String> claves) {
        for (String k : claves) {
            if (f.s(k).isEmpty()) {
                throw new DatoInvalido(k, "campo obligatorio vacio");
            }
        }
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
