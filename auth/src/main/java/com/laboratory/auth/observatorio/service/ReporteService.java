package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.ReporteArchivo;
import com.laboratory.auth.observatorio.api.dto.ReporteDocumento;
import com.laboratory.auth.observatorio.api.dto.ReporteHistorialItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;

    public void guardar(String email, ReporteDocumento documento, byte[] pdf) {
        String tablero = documento.tablero() == null || documento.tablero().isBlank()
                ? "Reporte institucional" : documento.tablero();
        String filtros = String.join("; ", documento.filtrosAplicados());
        String nombre = "informe-observatorio-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pdf";
        jdbc.update("INSERT INTO reporte_generado "
                + "(id_reporte, email, titulo, tipo, filtros, fecha_generacion, nombre_archivo, archivo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), email, "Informe institucional del Observatorio", tablero,
                filtros, Timestamp.valueOf(LocalDateTime.now()), nombre, pdf);
    }

    public List<ReporteHistorialItem> historial(String email) {
        return jdbc.query("SELECT id_reporte, titulo, tipo, filtros, fecha_generacion, nombre_archivo "
                        + "FROM reporte_generado WHERE email = ? ORDER BY fecha_generacion DESC",
                (rs, i) -> {
                    Timestamp fecha = rs.getTimestamp("fecha_generacion");
                    return new ReporteHistorialItem(
                            rs.getString("id_reporte"),
                            rs.getString("titulo"),
                            rs.getString("tipo"),
                            rs.getString("filtros"),
                            fecha == null ? "" : fecha.toLocalDateTime().format(FECHA),
                            rs.getString("nombre_archivo"));
                }, email);
    }

    public ReporteArchivo archivo(String id, String email) {
        List<ReporteArchivo> out = jdbc.query(
                "SELECT archivo, nombre_archivo FROM reporte_generado WHERE id_reporte = ? AND email = ?",
                (rs, i) -> new ReporteArchivo(rs.getBytes("archivo"), rs.getString("nombre_archivo")),
                id, email);
        if (out.isEmpty()) {
            throw new IllegalArgumentException("El reporte solicitado no existe");
        }
        return out.get(0);
    }
}
