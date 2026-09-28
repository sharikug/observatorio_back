package com.laboratory.auth.observatorio.ia.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * HU-13: declara cuando una solicitud queda fuera del alcance vigente.
 * Catalogo de exclusiones derivado del documento de alcance del modulo de IA.
 */
@Service
public class AlcanceService {

    private static final List<String> EXCLUSIONES = List.of(
            "prediccion", "predice", "predecir", "pronostico", "pronostica", "proyeccion",
            "escenario futuro", "decide automaticamente", "decision automatica", "decidir por mi",
            "recomienda automaticamente", "toma de decisiones automatica", "minciencias en vivo",
            "cv lac", "cvlac", "gruplac", "scienti", "scopus", "web of science",
            "internet", "google", "busca en la web", "pagina externa", "fuente externa",
            "dato personal", "opinion personal", "consulta legal", "asesoria juridica");

    public boolean fueraDeAlcance(String pregunta) {
        String normalizada = normalizar(pregunta);
        return EXCLUSIONES.stream().anyMatch(normalizada::contains);
    }

    public String mensajeFueraDeAlcance() {
        return "Esa solicitud esta fuera del alcance vigente del asistente del Observatorio. "
                + "El modulo responde unicamente con informacion de las fuentes institucionales "
                + "autorizadas (datos estructurados del Observatorio, contenido publicado y "
                + "documentos cargados), y no realiza predicciones, decisiones automaticas ni "
                + "consultas a fuentes externas no validadas. "
                + "Si necesitas este servicio, contacta el canal institucional de la Direccion de "
                + "Investigacion para priorizarlo en el backlog.";
    }

    private String normalizar(String texto) {
        String base = texto == null ? "" : texto;
        return Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
