package com.laboratory.auth.observatorio.ia.api.dto;

/**
 * @param conversacionId HU-17: si viene, la pregunta se continua en esa conversacion
 *                       y el modelo recibe los turnos anteriores.
 */
public record ChatRequest(String pregunta, String conversacionId) {
}
