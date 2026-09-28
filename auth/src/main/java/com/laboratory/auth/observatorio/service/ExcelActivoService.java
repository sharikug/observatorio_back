package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.ExcelCargadoItem;
import com.laboratory.auth.observatorio.api.dto.ExcelHistorial;
import com.laboratory.auth.observatorio.api.dto.ImportarResumen;
import com.laboratory.auth.observatorio.api.dto.ReporteArchivo;
import com.laboratory.auth.observatorio.api.dto.ValidacionResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Excel activo: en todo momento hay como maximo uno, y es el unico que alimenta
 * dashboards e IA. Los anteriores se conservan en la tabla para consultarlos, nunca
 * para mezclarlos con el activo.
 *
 * <p>La unicidad no se trusts de la aplicacion: la garantiza el indice unico parcial
 * de schema.sql sobre excel_cargado(estado) WHERE estado = 'ACTIVO'.
 */
@Service
@RequiredArgsConstructor
public class ExcelActivoService {

    public static final String ACTIVO = "ACTIVO";
    public static final String HISTORICO = "HISTORICO";
    public static final String RECHAZADO = "RECHAZADO";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ExcelValidacionService validacionService;
    private final ImportadorService importadorService;

    /**
     * Valida, purga los datos del Excel anterior e importa el nuevo como ACTIVO.
     *
     * <p>Si el archivo tiene errores criticos no se toca nada: el Excel anterior sigue
     * siendo el activo. Si tiene advertencias exige confirmacion explicita, porque el
     * frontend no es una frontera de confianza.
     */
    @Transactional
    public Resultado activar(MultipartFile archivo, String usuario, boolean confirmar) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        ValidacionResultado validacion = validacionService.validar(archivo);
        if (!validacion.puedeContinuar()) {
            throw new IllegalArgumentException("El archivo no puede procesarse debido a "
                    + validacion.criticas() + " errores criticos. " + validacion.resumenCriticos()
                    + " El Excel activo actual no fue modificado.");
        }
        if (!validacion.valido() && !confirmar) {
            throw new IllegalArgumentException("El archivo tiene " + validacion.advertencias()
                    + " advertencias. Debe confirmarlas explicitamente para activarlo.");
        }

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage());
        }

        // El Excel vigente deja de ser activo ANTES de registrar el nuevo, para no
        // violar el indice unico parcial.
        jdbc.update("UPDATE excel_cargado SET estado = ?, fecha_historico = ? WHERE estado = ?",
                HISTORICO, Timestamp.valueOf(LocalDateTime.now()), ACTIVO);

        // Se purga lo que trajo el Excel anterior para que los dashboards y la IA no
        // mezclen archivos. Las filas corregidas a mano se preservan.
        importadorService.limpiar();

        ImportarResumen resumen = importadorService.importar(archivo);
        return registrar(contenido, validacion, usuario, resumen);
    }

    private Resultado registrar(byte[] contenido, ValidacionResultado validacion,
                                String usuario, ImportarResumen resumen) {
        String id = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();
        jdbc.update("INSERT INTO excel_cargado (id_excel, nombre, tamano, contenido, estado, "
                        + "fecha_carga, usuario, valido, criticas, advertencias, "
                        + "total_inconsistencias, detalle_validacion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, validacion.archivo(), (long) contenido.length, contenido, ACTIVO,
                Timestamp.valueOf(ahora), usuario, validacion.valido(),
                validacion.criticas(), validacion.advertencias(), validacion.totalInconsistencias(),
                String.join(" | ", validacion.inconsistencias().stream()
                        .map(i -> i.tipo() + " " + i.celda() + " " + i.mensaje()).toList()));
        ExcelCargadoItem excel = new ExcelCargadoItem(id, validacion.archivo(), contenido.length,
                ACTIVO, ahora.format(FECHA), "", usuario, validacion.valido(), validacion.criticas(),
                validacion.advertencias(), validacion.totalInconsistencias(), true);
        return new Resultado(excel, resumen);
    }

    public Optional<ExcelCargadoItem> activo() {
        return jdbc.query("SELECT * FROM excel_cargado WHERE estado = ?", this::mapear, ACTIVO)
                .stream().findFirst();
    }

    public ExcelHistorial historial() {
        return new ExcelHistorial(activo().orElse(null),
                jdbc.query("SELECT * FROM excel_cargado WHERE estado <> ? ORDER BY fecha_carga DESC",
                        this::mapear, ACTIVO));
    }

    public ReporteArchivo contenido(String id) {
        List<ReporteArchivo> out = jdbc.query(
                "SELECT contenido, nombre FROM excel_cargado WHERE id_excel = ?",
                (rs, i) -> new ReporteArchivo(rs.getBytes("contenido"), rs.getString("nombre")), id);
        if (out.isEmpty()) {
            throw new IllegalArgumentException("El archivo solicitado no existe en el historial");
        }
        return out.get(0);
    }

    private ExcelCargadoItem mapear(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp carga = rs.getTimestamp("fecha_carga");
        Timestamp historico = rs.getTimestamp("fecha_historico");
        return new ExcelCargadoItem(
                rs.getString("id_excel"),
                rs.getString("nombre"),
                rs.getLong("tamano"),
                rs.getString("estado"),
                carga == null ? "" : carga.toLocalDateTime().format(FECHA),
                historico == null ? "" : historico.toLocalDateTime().format(FECHA),
                rs.getString("usuario"),
                rs.getBoolean("valido"),
                rs.getInt("criticas"),
                rs.getInt("advertencias"),
                rs.getInt("total_inconsistencias"),
                ACTIVO.equals(rs.getString("estado")));
    }

    /** Lo que devuelve la activacion: el Excel ya activo y el resumen de lo importado. */
    public record Resultado(ExcelCargadoItem excel, ImportarResumen importacion) {
    }
}
