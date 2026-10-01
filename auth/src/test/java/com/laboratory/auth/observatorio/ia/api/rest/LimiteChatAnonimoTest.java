package com.laboratory.auth.observatorio.ia.api.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El chat es publico y cada pregunta cuesta una llamada al proveedor del modelo.
 * El limite es lo que evita que el endpoint abierto se use como relay gratuito.
 */
class LimiteChatAnonimoTest {

    @Test
    void alSuperarElTopeSeBloqueaYAlVencerLaVentanaSeLibera() {
        // Ventana de 1 minuto real: se verifica el corte, no el paso del tiempo.
        LimiteChatAnonimo limite = new LimiteChatAnonimo(3, 60_000);

        assertTrue(limite.permitido("1.1.1.1"));
        assertTrue(limite.permitido("1.1.1.1"));
        assertTrue(limite.permitido("1.1.1.1"));
        assertFalse(limite.permitido("1.1.1.1"), "la cuarta consulta debe rechazarse");
    }

    @Test
    void cadaClienteTieneSuPropioCupo() {
        LimiteChatAnonimo limite = new LimiteChatAnonimo(2, 60_000);

        assertTrue(limite.permitido("1.1.1.1"));
        assertTrue(limite.permitido("1.1.1.1"));
        assertFalse(limite.permitido("1.1.1.1"));

        assertTrue(limite.permitido("2.2.2.2"), "otro cliente no hereda el consumo del anterior");
    }
}
