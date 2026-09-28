package com.laboratory.auth.observatorio.ia.conector;

import java.util.List;

/**
 * HU-12: contrato comun de fuente de datos. Incorporar un conector nuevo no
 * requiere tocar el motor de generacion de respuestas.
 */
public interface ConectorFuente {
    String id();

    String descripcion();

    List<String> tablas();
}
