package com.laboratory.auth.observatorio.api.dto;

/**
 * Tipos de inconsistencia que puede reportar la validacion previa a la importacion.
 *
 * <p>critico = true impide procesar el archivo y el frontend no debe ofrecer "Continuar".
 * critico = false es una advertencia: el archivo se puede procesar, pero conviene revisarlo.
 */
public enum TipoInconsistencia {

    ARCHIVO_NO_EXCEL("El archivo no es un Excel", true),
    ARCHIVO_CORRUPTO("El archivo no se pudo leer", true),
    SIN_HOJAS("El archivo no tiene hojas", true),
    SIN_ENCABEZADOS("La hoja no tiene encabezados", true),
    ENCABEZADO_FALTANTE("Falta un encabezado obligatorio", true),
    ENCABEZADO_DESCONOCIDO("Columna no reconocida por el modelo de datos", true),

    HOJA_FALTANTE("Falta una hoja esperada", false),
    HOJA_NO_RECONOCIDA("Hoja que el sistema no procesa", false),
    HOJA_VACIA("La hoja esta vacia", false),
    ENCABEZADO_VACIO("Encabezado vacio", false),
    ENCABEZADO_DUPLICADO("Encabezado duplicado", false),
    FILA_VACIA("Fila completamente vacia", false),
    COLUMNA_VACIA("Columna sin datos", false),
    CELDA_VACIA("Celda obligatoria vacia", false),
    TIPO_INVALIDO("Tipo de dato incorrecto", false),
    CANTIDAD_COLUMNAS("La fila no tiene todas las columnas del encabezado", false),
    DUPLICADO("Registro duplicado en el archivo", false),
    TRUNCADO("La lista de inconsistencias fue truncada", false);

    private final String etiqueta;
    private final boolean critico;

    TipoInconsistencia(String etiqueta, boolean critico) {
        this.etiqueta = etiqueta;
        this.critico = critico;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean critico() {
        return critico;
    }
}
