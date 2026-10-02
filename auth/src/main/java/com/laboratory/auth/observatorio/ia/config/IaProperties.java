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
        /** HU-21: MODEL_PROVIDER. "gemini" usa Google AI Studio; "openrouter", OpenRouter. */
        private String proveedor = "gemini";
        /**
         * HU-21: MODEL_NAME. Por defecto flash-lite: el modulo encadena varias llamadas
         * por pregunta y el razonamiento de gemini-3.5-flash multiplica la latencia.
         */
        private String nombre = "gemini-3.5-flash-lite";
        /** HU-21: EMBEDDING_MODEL. */
        private String embedding = "gemini-embedding-001";
        private String apiKey;
        /** Variable de entorno que lleva la clave, solo para nombrar el aviso al admin. */
        private String variableClave = "GEMINI_API_KEY";
        private String baseUrl = "https://generativelanguage.googleapis.com";
        private int maxFragmentos = 5;
        private double temperatura = 0.2;
        private int maxTokens = 4096;
        /**
         * Tokens de razonamiento. Null deja el valor por defecto del modelo, que es lo
         * recomendado en Gemini 3: alli el control es thinkingLevel y no thinkingBudget,
         * asi que fijarlo aqui solo aplica a los modelos anteriores.
         */
        private Integer presupuestoPensamiento;
        /**
         * Dimensiones del vector. Gemini entrena con Matryoshka, asi que truncar a 768
         * cuesta muy poca precision (MTEB 67.99 frente a 68.16 de 3072) y reduce a una
         * cuarta parte lo que ocupa cada fila de ia_fragmento.
         */
        private int dimensionesEmbedding = 768;
        /** Fragmentos por peticion de embedding. El tier gratis se agota por minuto. */
        private int loteEmbeddings = 25;
        /**
         * Reintentos ante fallos transitorios. El tier gratuito devuelve picos de demanda
         * (503) y cortes por limite por minuto (429) de forma regular; sin reintentar, el
         * asistente falla de forma aleatoria ante el usuario.
         */
        private int reintentos = 3;
        /** HU-09: dominios de egreso permitidos (solo el proveedor del modelo). */
        private List<String> dominiosPermitidos = List.of("generativelanguage.googleapis.com");
    }

    @Getter
    @Setter
    public static class Conectores {
        private List<String> activos = List.of("postgres-observatorio");
    }
}
