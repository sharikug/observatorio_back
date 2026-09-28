package com.laboratory.auth.observatorio.ia.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "ia")
@Getter
@Setter
public class IaProperties {

    private Gemini gemini = new Gemini();
    private Conectores conectores = new Conectores();

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String modelo = "gemini-2.0-flash";
        private String modeloEmbeddings = "text-embedding-004";
        private int maxFragmentos = 5;
        // HU-09: dominios de egreso permitidos del proveedor del modelo.
        private List<String> dominiosPermitidos = List.of("generativelanguage.googleapis.com");
    }

    @Getter
    @Setter
    public static class Conectores {
        private List<String> activos = List.of("postgres-observatorio");
    }
}
