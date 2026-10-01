package com.laboratory.auth.observatorio.ia.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuracion centralizada del modulo de IA (HU-09/HU-21). Cambiar de modelo o
 * de proveedor es editar estas propiedades, no el codigo.
 */
@Component
@ConfigurationProperties(prefix = "ia")
@Getter
@Setter
public class IaProperties {

    private Modelo modelo = new Modelo();
    private Conectores conectores = new Conectores();

    @Getter
    @Setter
    public static class Modelo {
        /** HU-21: MODEL_PROVIDER. */
        private String proveedor = "openrouter";
        /** HU-21: MODEL_NAME. */
        private String nombre = "google/gemini-2.0-flash-001";
        /** HU-21: EMBEDDING_MODEL. */
        private String embedding = "openai/text-embedding-3-small";
        private String apiKey;
        private String baseUrl = "https://openrouter.ai/api/v1";
        private int maxFragmentos = 5;
        /** HU-09: unico dominio de egreso permitido (el proveedor del modelo). */
        private List<String> dominiosPermitidos = List.of("openrouter.ai");
    }

    @Getter
    @Setter
    public static class Conectores {
        private List<String> activos = List.of("postgres-observatorio");
    }
}
