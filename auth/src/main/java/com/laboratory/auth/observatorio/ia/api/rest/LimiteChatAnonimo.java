package com.laboratory.auth.observatorio.ia.api.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita cuantas consultas se pueden hacer al chat por cliente y por minuto.
 *
 * <p>El chat es publico, y cada pregunta consume llamadas de pago al proveedor del
 * modelo. Sin este limite, cualquier script podria vaciar la cuota del Observatory con
 * un bucle: el endpoint abierto no puede ser un relay gratuito.
 *
 * <p>ponytail: ventana deslizante en memoria y por IP. Vale para una sola instancia, que
 * es el despliegue del proyecto; con varias instancias hace falta un contador compartido
 * (Redis) o un limite en el proxy. Ojo: sin un proxy delante, todas las peticiones
 * llegan desde 127.0.0.1 y comparten un unico cupo. Delante de un proxy hay que activar
 * server.forward-headers-strategy=NATIVE para que X-Forwarded-For tenga sentido.
 *
 * <p>La clave es la IP y no X-Anon-Id a proposito: esa cabecera la elige el cliente, así
 * que como contador de abuso se saldaria cambiando una letra.
 */
@Component
public class LimiteChatAnonimo extends OncePerRequestFilter {

    private static final String RUTA_CHAT = "/api/ia/chat";
    private static final int MAX_POR_MINUTO = 20;
    private static final long VENTANA_MS = 60_000;
    /** Techo del mapa: se poda cuando se supera, para no crecer sin limite. */
    private static final int MAX_CLIENTES = 5000;

    private final Map<String, Deque<Long>> consultas = new ConcurrentHashMap<>();
    private final int maximo;
    private final long ventana;

    public LimiteChatAnonimo() {
        this(MAX_POR_MINUTO, VENTANA_MS);
    }

    LimiteChatAnonimo(int maximo, long ventana) {
        this.maximo = maximo;
        this.ventana = ventana;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"POST".equals(request.getMethod()) || !request.getRequestURI().endsWith(RUTA_CHAT)) {
            chain.doFilter(request, response);
            return;
        }
        if (!permitido(cliente(request))) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"message\":\"Alcanzaste el limite de consultas del asistente. Espera un minuto e intentalo de nuevo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Ventana deslizante: cuenta solo las consultas de los ultimos {@code ventana} ms. */
    boolean permitido(String cliente) {
        long ahora = System.currentTimeMillis();
        if (consultas.size() > MAX_CLIENTES) {
            podar(ahora);
        }
        Deque<Long> marcas = consultas.computeIfAbsent(cliente, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > ventana) {
                marcas.pollFirst();
            }
            if (marcas.size() >= maximo) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private void podar(long ahora) {
        consultas.entrySet().removeIf(e -> {
            Deque<Long> marcas = e.getValue();
            synchronized (marcas) {
                while (!marcas.isEmpty() && ahora - marcas.peekFirst() > ventana) {
                    marcas.pollFirst();
                }
                return marcas.isEmpty();
            }
        });
    }

    /** X-Forwarded-For primero (si hay proxy), si no la IP de la conexion. */
    private String cliente(HttpServletRequest request) {
        String cabecera = request.getHeader("X-Forwarded-For");
        if (cabecera != null && !cabecera.isBlank()) {
            return cabecera.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "desconocido" : request.getRemoteAddr();
    }
}
