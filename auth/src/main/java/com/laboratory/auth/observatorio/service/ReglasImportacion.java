package com.laboratory.auth.observatorio.service;

import java.util.List;
import java.util.Set;

/**
 * Estructura que el sistema espera del Excel institucional.
 *
 * <p>Fuente unica de verdad: la usan {@link ImportadorService} para importar y
 * {@link ExcelValidacionService} para validar, de modo que ambas nunca se desalinean.
 * Para cambiar el modelo de datos (agregar una columna, una hoja, un tipo) se edita
 * solamente esta clase.
 *
 * <p>Las columnas en {@code claves} son obligatorias porque sin ellas la importacion
 * rechaza la fila ({@code validarClaves}) o no puede armar el ON CONFLICT. El resto de
 * columnas son opcionales: una celda vacia ahi no es un error.
 */
public final class ReglasImportacion {

    /**
     * @param hoja          nombre exacto de la hoja en el Excel
     * @param tabla         tabla destino
     * @param claves        columnas que forman la clave primaria de la fila
     * @param columnas      todas las columnas que la tabla acepta
     * @param numericas     columnas que deben contener un numero
     * @param controlManual si es true, no sobrescribe registros corregidos a mano
     * @param tieneOrigen   si es true, la tabla tiene columna 'origen' y al activar un
     *                      Excel nuevo solo se purgan sus filas IMPORTADO, preservando
     *                      las corregidas a mano. Si es false, la tabla se purga entera.
     */
    public record Regla(
            String hoja,
            String tabla,
            List<String> claves,
            List<String> columnas,
            Set<String> numericas,
            boolean controlManual,
            boolean tieneOrigen
    ) {
        public boolean esClave(String encabezado) {
            return claves.contains(encabezado);
        }
    }

    public static final List<Regla> HOJAS = List.of(
            new Regla("Facultades", "facultad", List.of("id_facultad"),
                    List.of("id_facultad", "nombre_facultad"), Set.of(), false, false),
            new Regla("Programas", "programa", List.of("id_programa"),
                    List.of("id_programa", "nombre_programa"), Set.of(), false, false),
            new Regla("Unidad_Regional", "unidad_regional", List.of("id_unidad_regional"),
                    List.of("id_unidad_regional", "nombre_unidad_regional"), Set.of(), false, false),
            new Regla("Lineas_Translocales", "linea_translocal", List.of("id_linea"),
                    List.of("id_linea", "nombre_linea"), Set.of(), false, false),
            new Regla("ODS", "ods", List.of("id_ods"),
                    List.of("id_ods", "numero_ods", "nombre_ods"), Set.of("numero_ods"), false, false),
            new Regla("Investigadores", "investigador", List.of("id_investigador"),
                    List.of("id_investigador", "nombre_investigador", "id_unidad_regional",
                            "id_facultad", "id_programa"), Set.of(), false, false),
            new Regla("Facultades_Grupo", "facultad_grupo", List.of("id_facultad_grupo"),
                    List.of("id_facultad_grupo", "nombre_facultad_grupo"), Set.of(), false, false),
            new Regla("Unidad_Regional_Grupo", "unidad_regional_grupo", List.of("id_unidad_regional_grupo"),
                    List.of("id_unidad_regional_grupo", "nombre_unidad_regional_grupo"), Set.of(), false, false),
            new Regla("Programas_Grupo", "programa_grupo", List.of("id_programa_grupo"),
                    List.of("id_programa_grupo", "nombre_programa_grupo"), Set.of(), false, false),
            new Regla("Grupos", "grupo", List.of("id_grupo"),
                    List.of("id_grupo", "nombre_grupo", "categoria_minciencias", "lider_grupo",
                            "facultad_referencia", "sede_referencia", "id_facultad_grupo",
                            "id_unidad_regional_grupo"), Set.of(), false, false),
            new Regla("Proyectos", "proyecto", List.of("codigo_proyecto"),
                    List.of("id_proyecto", "codigo_proyecto", "nombre_proyecto", "convocatoria", "anio",
                            "periodo", "estado_proyecto", "tipo_investigacion", "id_unidad_regional",
                            "id_facultad", "id_programa", "porcentaje_avance_tecnico", "tiene_convenio",
                            "objetivo_general", "objetivos_especificos", "palabras_clave", "origen"),
                    Set.of("anio"), true, true),
            new Regla("Participacion", "participacion", List.of("id_participacion"),
                    List.of("id_participacion", "id_proyecto", "id_investigador", "id_grupo", "rol",
                            "orden_participacion"), Set.of("orden_participacion"), false, false),
            new Regla("Proyecto_Equipo", "proyecto_equipo", List.of("id_proyecto"),
                    List.of("id_proyecto", "id_investigador_principal", "tamano_equipo"),
                    Set.of("tamano_equipo"), false, false),
            new Regla("Proyecto_Colaboracion_Grupo", "proyecto_colaboracion_grupo", List.of("id_colaboracion"),
                    List.of("id_colaboracion", "id_proyecto", "id_grupo_origen", "nombre_grupo_origen",
                            "id_grupo_destino", "nombre_grupo_destino"), Set.of(), false, false),
            new Regla("Proyecto_Linea", "proyecto_linea", List.of("id_proyecto", "id_linea"),
                    List.of("id_proyecto", "id_linea"), Set.of(), false, false),
            new Regla("Proyecto_ODS", "proyecto_ods", List.of("id_proyecto", "id_ods"),
                    List.of("id_proyecto", "id_ods"), Set.of(), false, false),
            new Regla("Proyecto_Grupo", "proyecto_grupo", List.of("id_proyecto", "id_grupo"),
                    List.of("id_proyecto", "id_grupo", "rol_grupo_proyecto"), Set.of(), false, false),
            new Regla("Grupo_Programa", "grupo_programa", List.of("id_grupo", "id_programa_grupo"),
                    List.of("id_grupo", "id_programa_grupo"), Set.of(), false, false)
    );

    private ReglasImportacion() {
    }

    public static List<String> nombresHojas() {
        return HOJAS.stream().map(Regla::hoja).toList();
    }
}
