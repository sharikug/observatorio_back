package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.api.dto.FuenteRecuperada;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HU-10: registra usuario, marca temporal, pregunta, fragmentos recuperados,
 * respuesta, SQL y latencia; y exporta el registro en formato tabular.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public void registrar(String email, String rol, String pregunta, List<FuenteRecuperada> fuentes,
                          String sql, String respuesta, boolean enAlcance, long latenciaMs) {
        String referencias = fuentes == null ? "" : fuentes.stream()
                .map(FuenteRecuperada::cita)
                .collect(Collectors.joining("; "));
        jdbc.update("INSERT INTO ia_auditoria "
                        + "(email, rol, pregunta, fragmentos, sql_generado, respuesta, en_alcance, latencia_ms, fecha) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                email, rol, pregunta, referencias, sql, respuesta, enAlcance, latenciaMs,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    public List<Map<String, Object>> listar(LocalDateTime desde, LocalDateTime hasta) {
        return jdbc.queryForList("SELECT id_auditoria, email, rol, pregunta, fragmentos, sql_generado, "
                + "respuesta, en_alcance, latencia_ms, fecha FROM ia_auditoria "
                + "WHERE fecha >= ? AND fecha <= ? ORDER BY fecha DESC",
                Timestamp.valueOf(desde), Timestamp.valueOf(hasta));
    }

    public String exportarCsv(LocalDateTime desde, LocalDateTime hasta) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,email,rol,pregunta,fragmentos,sql,respuesta,en_alcance,latencia_ms,fecha\n");
        for (Map<String, Object> fila : listar(desde, hasta)) {
            sb.append(csv(fila.get("id_auditoria"))).append(',')
                    .append(csv(fila.get("email"))).append(',')
                    .append(csv(fila.get("rol"))).append(',')
                    .append(csv(fila.get("pregunta"))).append(',')
                    .append(csv(fila.get("fragmentos"))).append(',')
                    .append(csv(fila.get("sql_generado"))).append(',')
                    .append(csv(fila.get("respuesta"))).append(',')
                    .append(csv(fila.get("en_alcance"))).append(',')
                    .append(csv(fila.get("latencia_ms"))).append(',')
                    .append(csv(fila.get("fecha"))).append('\n');
        }
        return sb.toString();
    }

    private String csv(Object valor) {
        String texto = valor == null ? "" : String.valueOf(valor);
        if (valor instanceof Timestamp t) {
            texto = t.toLocalDateTime().format(FECHA);
        }
        return '"' + texto.replace("\"", "\"\"").replace("\n", " ") + '"';
    }
}
