package com.laboratory.auth.observatorio.ia.client;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * HU-09: el modulo vive en un entorno cerrado y su unico egreso es el proveedor del
 * modelo. Este guardián es el unico lugar donde se decide si una URL puede usarse,
 * para que ningun cliente pueda salirse de la lista de dominios permitidos.
 *
 * <p>La comparacion es sobre el host real de la URL, no sobre la cadena completa. Con
 * {@code contains} un destino como {@code https://atacante.example/?ref=openrouter.ai}
 * pasaba el filtro por llevar el dominio permitido en un parametro de consulta.
 */
final class DominioPermitido {

    private DominioPermitido() {
    }

    /**
     * @throws IllegalStateException si el destino no esta en la lista blanca.
     */
    static void exigir(List<String> permitidos, String baseUrl) {
        String host = hostDe(baseUrl);
        boolean permitido = permitidos.stream()
                .filter(d -> d != null && !d.isBlank())
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .anyMatch(host::equals);
        if (!permitido) {
            throw new IllegalStateException("Destino de egreso no permitido por el alcance cerrado: "
                    + host + " (permitidos: " + String.join(", ", permitidos) + ")");
        }
    }

    /** Dominio de la URL, sin puerto ni subdominio de wildcard. */
    private static String hostDe(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("La URL del proveedor no tiene dominio: " + url);
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La URL del proveedor no es valida: " + url, e);
        }
    }
}