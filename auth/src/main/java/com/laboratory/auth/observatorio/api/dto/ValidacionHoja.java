package com.laboratory.auth.observatorio.api.dto;

/** Resumen por hoja, para que el frontend diga "3 en Proyectos, 2 en Investigadores". */
public record ValidacionHoja(String nombre, int filas, int inconsistencias, int criticas) {
}
