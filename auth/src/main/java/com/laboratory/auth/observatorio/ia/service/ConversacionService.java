package com.laboratory.auth.observatorio.ia.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-17/HU-30: memoria conversacional. Guarda que se pregunto y que se respondio,
 * y devuelve los ultimos turnos como texto para que el modelo resuelva referencias
 * como "y esos cuantos proyectos tienen".
 *
 * <p>HU-08: el historial es contexto, no permiso. Nunca se usa para recuperar
 * informacion: cada turno vuelve a pasar por TextToSqlService y RagService, que
 * filtran por rol. Por eso una conversacion ajena devuelve 404 y no 403: ni siquiera
 * se confirma que exista.
 */
@Service
public class ConversacionService {

    /** Turnos que viajan al prompt. Mas que esto solo gasta tokens sin aportar. */
    private static final int TURNOS = 6;

    private final JdbcTemplate jdbc;

    public ConversacionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String crear(String email) {
        String id = UUID.randomUUID().toString();
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO ia_conversacion (id_conversacion, email, titulo, creado, actualizado) "
                + "VALUES (?, ?, ?, ?, ?)", id, email, "Nueva conversacion", ahora, ahora);
        return id;
    }

    public List<Map<String, Object>> listar(String email) {
        return jdbc.queryForList("SELECT c.id_conversacion, c.titulo, c.creado, c.actualizado, "
                        + "(SELECT COUNT(*) FROM ia_mensaje m WHERE m.id_conversacion = c.id_conversacion) AS mensajes "
                        + "FROM ia_conversacion c WHERE c.email = ? ORDER BY c.actualizado DESC",
                email);
    }

    public List<Map<String, Object>> mensajes(String email, String id) {
        verificarPertenencia(email, id);
        return jdbc.queryForList("SELECT id_mensaje, rol, contenido, fuentes, en_alcance, creado "
                + "FROM ia_mensaje WHERE id_conversacion = ? ORDER BY id_mensaje", id);
    }

    public void borrar(String email, String id) {
        verificarPertenencia(email, id);
        jdbc.update("DELETE FROM ia_mensaje WHERE id_conversacion = ?", id);
        jdbc.update("DELETE FROM ia_conversacion WHERE id_conversacion = ?", id);
    }

    public void registrarMensaje(String idConversacion, String rol, String contenido,
                                 List<String> fuentes, Boolean enAlcance) {
        if (idConversacion == null || idConversacion.isBlank()) {
            return;
        }
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO ia_mensaje (id_conversacion, rol, contenido, fuentes, en_alcance, creado) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                idConversacion, rol, contenido, fuentes == null ? "" : String.join("; ", fuentes),
                enAlcance, ahora);
        // El titulo se toma del primer mensaje del usuario. El rol va como parametro:
        // ia_conversacion no tiene columna "rol", y escribirlo en el SQL hacia fallar
        // la actualizacion entera, es decir, perder la conversacion de todo el turno.
        jdbc.update("UPDATE ia_conversacion SET actualizado = ?, titulo = CASE WHEN ? = 'user' "
                        + "AND titulo = 'Nueva conversacion' THEN ? ELSE titulo END "
                        + "WHERE id_conversacion = ?",
                ahora, rol, recortar(contenido, 60), idConversacion);
    }

    private String recortar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max) + "...";
    }

    /**
     * Historial reciente como texto para el prompt. Se marca como contexto no
     * verificado para que el modelo no lo tome por dato vigente del archivo.
     */
    public String contexto(String email, String idConversacion) {
        if (idConversacion == null || idConversacion.isBlank()) {
            return "";
        }
        List<String> turnos = jdbc.query(
                "SELECT rol, contenido FROM ia_mensaje WHERE id_conversacion = ? ORDER BY id_mensaje DESC LIMIT ?",
                (rs, i) -> (rs.getString("rol").equals("user") ? "Usuario: " : "Asistente: ")
                        + recortar(rs.getString("contenido")), idConversacion, TURNOS * 2);
        if (turnos.isEmpty()) {
            return "";
        }
        // Copia antes de invertir: la lista que devuelve la consulta no siempre es mutable.
        List<String> orden = new java.util.ArrayList<>(turnos);
        java.util.Collections.reverse(orden);
        return "CONVERSACION PREVIA (contexto de referencia; puede estar desactualizada, "
                + "no la tomes como dato vigente del archivo):\n"
                + String.join("\n", orden) + "\n\n";
    }

    private void verificarPertenencia(String email, String id) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ia_conversacion WHERE id_conversacion = ? AND email = ?",
                Integer.class, id, email);
        if (total == null || total == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La conversacion no existe");
        }
    }

    private String recortar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= 600 ? texto : texto.substring(0, 600) + "...";
    }
}
