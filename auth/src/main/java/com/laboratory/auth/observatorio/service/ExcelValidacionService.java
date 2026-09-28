package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.TipoInconsistencia;
import com.laboratory.auth.observatorio.api.dto.ValidacionHoja;
import com.laboratory.auth.observatorio.api.dto.ValidacionInconsistencia;
import com.laboratory.auth.observatorio.api.dto.ValidacionResultado;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HU: valida el Excel institucional antes de importarlo. No escribe nada en la base de
 * datos: solo lee el archivo y dice que encontro y donde.
 *
 * <p>Las reglas salen de {@link ReglasImportacion}, la misma fuente que usa el
 * importador, asi que la validacion no puede quedar desalineada del procesamiento.
 *
 * <p>Sin estado mutable: dos cargas simultaneas no se pisan entre si.
 */
@Service
public class ExcelValidacionService {

    /** Tope de inconsistencias en la respuesta; el total informado sigue siendo el real. */
    private static final int MAX_INCONSISTENCIAS = 500;
    private static final Set<String> EXTENSIONES = Set.of("xlsx", "xls");
    /** Une las columnas clave al detectar duplicados; un caracter de control no aparece en una celda. */
    private static final String SEPARADOR_CLAVE = "\u0001";

    public ValidacionResultado validar(MultipartFile archivo) {
        String nombre = nombreArchivo(archivo);
        String extension = extension(nombre);
        if (!EXTENSIONES.contains(extension)) {
            return resultado(nombre, List.of(),
                    List.of(hoja("", 0, TipoInconsistencia.ARCHIVO_NO_EXCEL,
                            "El archivo '" + nombre + "' no es un Excel (.xlsx o .xls).",
                            "Descargue el archivo en formato Excel e intentelo de nuevo.")));
        }

        List<ValidacionInconsistencia> halladas = new ArrayList<>();
        List<ValidacionHoja> hojas = new ArrayList<>();
        DataFormatter fmt = new DataFormatter(Locale.ROOT);

        try (InputStream in = archivo.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                halladas.add(hoja("", 0, TipoInconsistencia.SIN_HOJAS,
                        "El archivo '" + nombre + "' no tiene ninguna hoja.",
                        "El archivo debe contener al menos la hoja 'Proyectos'."));
            } else {
                validarLibro(wb, fmt, halladas, hojas);
            }
        } catch (Exception e) {
            // Cualquier fallo de lectura (corrupto, .txt renombrado, .xls antiguo ilegible)
            // se reporta como archivo ilegible en vez de propagar un stack trace al usuario.
            halladas.add(hoja("", 0, TipoInconsistencia.ARCHIVO_CORRUPTO,
                    "No se pudo leer '" + nombre + "' como un libro de Excel" + detalle(e) + ".",
                    "Verifique que el archivo no este corrupto y que sea un .xlsx o .xls valido."));
        }

        return resultado(nombre, hojas, halladas);
    }

    private void validarLibro(Workbook wb, DataFormatter fmt,
                              List<ValidacionInconsistencia> out, List<ValidacionHoja> hojas) {
        Set<String> conocidas = new HashSet<>(ReglasImportacion.nombresHojas());
        Map<String, Integer> filasPorHoja = new LinkedHashMap<>();

        for (ReglasImportacion.Regla regla : ReglasImportacion.HOJAS) {
            Sheet sh = buscarHoja(wb, regla.hoja());
            if (sh == null) {
                out.add(hoja(regla.hoja(), 0, TipoInconsistencia.HOJA_FALTANTE,
                        "No se encontro la hoja '" + regla.hoja() + "', que el sistema si procesa.",
                        "Agregue la hoja '" + regla.hoja() + "' o continue si esos datos ya estaban cargados."));
                continue;
            }
            filasPorHoja.put(regla.hoja(), validarHoja(sh, regla, fmt, out));
        }

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String nombre = wb.getSheetName(i).trim();
            if (!conocidas.contains(nombre)) {
                out.add(hoja(wb.getSheetName(i), 0, TipoInconsistencia.HOJA_NO_RECONOCIDA,
                        "La hoja '" + wb.getSheetName(i) + "' no corresponde a ninguna tabla del sistema, "
                                + "por lo que su contenido se ignorara al importar.",
                        "Verifique el nombre de la hoja o eliminela del archivo."));
            }
        }

        for (Map.Entry<String, Integer> e : filasPorHoja.entrySet()) {
            String nombre = e.getKey();
            int criticas = (int) out.stream()
                    .filter(i -> i.hoja().equals(nombre) && i.tipo().critico()).count();
            int total = (int) out.stream().filter(i -> i.hoja().equals(nombre)).count();
            hojas.add(new ValidacionHoja(nombre, e.getValue(), total, criticas));
        }
    }

    private Sheet buscarHoja(Workbook wb, String nombre) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (nombre.equals(wb.getSheetName(i).trim())) {
                return wb.getSheetAt(i);
            }
        }
        return null;
    }

    /** @return cantidad de filas de datos (sin contar el encabezado). */
    private int validarHoja(Sheet sh, ReglasImportacion.Regla regla, DataFormatter fmt,
                            List<ValidacionInconsistencia> out) {
        String hoja = sh.getSheetName();
        var it = sh.iterator();
        if (!it.hasNext()) {
            out.add(hoja(hoja, 0, TipoInconsistencia.HOJA_VACIA,
                    "La hoja '" + hoja + "' no tiene ninguna fila.",
                    "Agregue el encabezado y los datos, o elimine la hoja del archivo."));
            return 0;
        }

        Row head = it.next();
        List<String> headers = leerEncabezados(head, fmt);
        if (headers.stream().allMatch(String::isEmpty)) {
            out.add(hoja(hoja, head.getRowNum() + 1, TipoInconsistencia.SIN_ENCABEZADOS,
                    "La hoja '" + hoja + "' no tiene encabezados: la primera fila esta vacia.",
                    "La primera fila debe contener los nombres de las columnas, por ejemplo "
                            + String.join(", ", regla.claves()) + "."));
            return 0;
        }
        int filaEncabezado = head.getRowNum() + 1;

        Map<String, Integer> posiciones = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h.isEmpty()) {
                out.add(celda(hoja, filaEncabezado, i, "",
                        TipoInconsistencia.ENCABEZADO_VACIO,
                        "Hay una columna sin nombre en '" + hoja + "'. Las columnas sin encabezado se "
                                + "descartan al importar, y sus datos se pierden.",
                        "Escriba el nombre de la columna o elimine la columna sobrante."));
                continue;
            }
            Integer previa = posiciones.putIfAbsent(h, i);
            if (previa != null) {
                out.add(celda(hoja, filaEncabezado, i, h,
                        TipoInconsistencia.ENCABEZADO_DUPLICADO,
                        "El encabezado '" + h + "' aparece dos veces, en las columnas "
                                + letra(previa) + " y " + letra(i) + ".",
                        "Renombre una de las dos columnas. Al importar solo se conserva el valor "
                                + "de la columna " + letra(i) + "."));
            }
        }

        for (String clave : regla.claves()) {
            if (!posiciones.containsKey(clave)) {
                out.add(hoja(hoja, filaEncabezado, TipoInconsistencia.ENCABEZADO_FALTANTE,
                        "A la hoja '" + hoja + "' le falta la columna obligatoria '" + clave + "', "
                                + "que identifica cada fila.",
                        "Agregue la columna '" + clave + "' con un valor unico en cada fila."));
            }
        }

        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (!h.isEmpty() && !regla.columnas().contains(h)) {
                out.add(celda(hoja, filaEncabezado, i, h,
                        TipoInconsistencia.ENCABEZADO_DESCONOCIDO,
                        "La columna '" + h + "' no existe en la tabla '" + regla.tabla() + "', "
                                + "así que ninguna fila de esta hoja podra guardarse.",
                        "Revise el encabezado. Las columnas validas son: "
                                + String.join(", ", regla.columnas()) + "."));
            }
        }

        int[] conDatos = new int[headers.size()];
        Set<String> firmas = new HashSet<>();
        int filas = 0;

        while (it.hasNext()) {
            Row r = it.next();
            int numeroFila = r.getRowNum() + 1;
            int ultima = r.getLastCellNum();
            if (ultima > headers.size()) {
                out.add(hoja(hoja, numeroFila, TipoInconsistencia.CANTIDAD_COLUMNAS,
                        "La fila " + numeroFila + " de '" + hoja + "' tiene " + ultima
                                + " columnas y el encabezado define " + headers.size() + ".",
                        "Revise que la fila no tenga columnas de mas."));
            }

            String[] valores = new String[headers.size()];
            boolean vacia = true;
            for (int i = 0; i < headers.size(); i++) {
                Cell c = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                valores[i] = c == null ? "" : fmt.formatCellValue(c).trim();
                if (!valores[i].isEmpty()) {
                    vacia = false;
                    conDatos[i]++;
                }
            }
            if (vacia) {
                out.add(hoja(hoja, numeroFila, TipoInconsistencia.FILA_VACIA,
                        "La fila " + numeroFila + " de '" + hoja + "' esta completamente vacia.",
                        "Complete la fila o elimine la fila del archivo."));
                continue;
            }
            filas++;

            String[] clave = new String[regla.claves().size()];
            boolean claveCompleta = true;
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i);
                if (h.isEmpty()) {
                    continue;
                }
                if (regla.esClave(h)) {
                    int pos = regla.claves().indexOf(h);
                    clave[pos] = valores[i];
                    if (valores[i].isEmpty()) {
                        claveCompleta = false;
                        out.add(celda(hoja, numeroFila, i, h, TipoInconsistencia.CELDA_VACIA,
                                "La celda esta vacia y la columna '" + h + "' es obligatoria: "
                                        + "es la que identifica la fila.",
                                "Ingrese un valor en '" + h + "'."));
                    }
                }
                if (!valores[i].isEmpty() && regla.numericas().contains(h) && !esNumero(valores[i])) {
                    out.add(celda(hoja, numeroFila, i, h, TipoInconsistencia.TIPO_INVALIDO,
                            "Se esperaba un numero en '" + h + "' y se encontro el valor '" + valores[i] + "'.",
                            "Reemplace '" + valores[i] + "' por un valor numerico."));
                }
            }

            if (claveCompleta && !firmas.add(String.join(SEPARADOR_CLAVE, clave))) {
                // Se ancla en la celda de la primera columna clave, que es la que se repite.
                String ancla = regla.claves().get(0);
                out.add(celda(hoja, numeroFila, posiciones.get(ancla), ancla, TipoInconsistencia.DUPLICADO,
                        "La fila " + numeroFila + " de '" + hoja + "' repite el valor ("
                                + describeClave(regla, clave) + ") que ya aparece en otra fila.",
                        "Elimine la fila repetida o cambie el valor de " + describeClave(regla, clave) + "."));
            }
        }

        if (filas == 0) {
            out.add(hoja(hoja, filaEncabezado, TipoInconsistencia.HOJA_VACIA,
                    "La hoja '" + hoja + "' tiene encabezado pero ninguna fila de datos.",
                    "Agregue las filas de datos o elimine la hoja del archivo."));
            return 0;
        }

        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (!h.isEmpty() && conDatos[i] == 0 && !regla.esClave(h)) {
                out.add(celda(hoja, filaEncabezado, i, h, TipoInconsistencia.COLUMNA_VACIA,
                        "La columna '" + h + "' de '" + hoja + "' no tiene ningun valor en sus " + filas
                                + " filas. Se guardara vacia en todos los registros.",
                        "Complete la columna o eliminela del archivo si no la necesita."));
            }
        }
        return filas;
    }

    private List<String> leerEncabezados(Row head, DataFormatter fmt) {
        int ultimo = head.getLastCellNum();
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < Math.max(ultimo, 0); i++) {
            Cell c = head.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            headers.add(c == null ? "" : fmt.formatCellValue(c).trim());
        }
        return headers;
    }

    private String describeClave(ReglasImportacion.Regla regla, String[] clave) {
        List<String> partes = new ArrayList<>();
        for (int i = 0; i < regla.claves().size(); i++) {
            partes.add(regla.claves().get(i) + " = " + clave[i]);
        }
        return String.join(", ", partes);
    }

    /** Misma regla que aplica ImportadorService al convertir a entero, para no contradecirse. */
    private boolean esNumero(String valor) {
        try {
            Double.parseDouble(valor.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private ValidacionResultado resultado(String archivo, List<ValidacionHoja> hojas,
                                         List<ValidacionInconsistencia> halladas) {
        int criticas = (int) halladas.stream().filter(i -> i.tipo().critico()).count();
        List<ValidacionInconsistencia> respuesta = List.copyOf(
                halladas.subList(0, Math.min(halladas.size(), MAX_INCONSISTENCIAS)));
        if (halladas.size() > MAX_INCONSISTENCIAS) {
            respuesta = new ArrayList<>(respuesta);
            respuesta.add(hoja("", 0, TipoInconsistencia.TRUNCADO,
                    "Se muestran las primeras " + MAX_INCONSISTENCIAS + " de " + halladas.size()
                            + " inconsistencias. Corija el archivo y validelo de nuevo para ver el resto.",
                    "Atienda primero las inconsistencias criticas y vuelva a validar."));
        }
        return new ValidacionResultado(archivo, halladas.isEmpty(), criticas == 0,
                halladas.size(), criticas, halladas.size() - criticas, List.copyOf(hojas), respuesta);
    }

    private ValidacionInconsistencia hoja(String hoja, int fila, TipoInconsistencia tipo,
                                          String mensaje, String sugerencia) {
        return new ValidacionInconsistencia(hoja, fila, "", "", "", tipo, mensaje, sugerencia);
    }

    private ValidacionInconsistencia celda(String hoja, int fila, int indice, String nombreColumna,
                                           TipoInconsistencia tipo, String mensaje, String sugerencia) {
        String col = letra(indice);
        return new ValidacionInconsistencia(hoja, fila, col, nombreColumna, col + fila, tipo,
                mensaje, sugerencia);
    }

    private static String letra(int indice) {
        return CellReference.convertNumToColString(indice);
    }

    private static String nombreArchivo(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename();
        return nombre == null || nombre.isBlank() ? "archivo" : nombre;
    }

    private static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static String detalle(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? "" : " (" + m + ")";
    }
}
