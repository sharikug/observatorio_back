package com.laboratory.auth.observatorio.service;

import com.laboratory.auth.observatorio.api.dto.ColaboracionGrupo;
import com.laboratory.auth.observatorio.api.dto.GrupoEnProyecto;
import com.laboratory.auth.observatorio.api.dto.GrupoInfo;
import com.laboratory.auth.observatorio.api.dto.GrupoProyecto;
import com.laboratory.auth.observatorio.api.dto.GruposDashboard;
import com.laboratory.auth.observatorio.api.dto.ParticipanteGrupo;
import com.laboratory.auth.observatorio.api.dto.ProyectoRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbc;

    public List<ProyectoRow> proyectos() {
        Map<String, String> facultades = mapa("SELECT id_facultad, nombre_facultad FROM facultad");
        Map<String, String> programas = mapa("SELECT id_programa, nombre_programa FROM programa");
        Map<String, String> regionales = mapa("SELECT id_unidad_regional, nombre_unidad_regional FROM unidad_regional");
        Map<String, String> investigadores = mapa("SELECT id_investigador, nombre_investigador FROM investigador");

        Map<String, String> principal = new HashMap<>();
        Map<String, Integer> equipos = new HashMap<>();
        jdbc.query("SELECT id_proyecto, id_investigador_principal, tamano_equipo FROM proyecto_equipo", rs -> {
            String id = rs.getString("id_proyecto");
            principal.put(id, rs.getString("id_investigador_principal"));
            equipos.put(id, getInt(rs, "tamano_equipo"));
        });

        Map<String, List<String>> lineas = new HashMap<>();
        jdbc.query("SELECT pl.id_proyecto AS p, lt.nombre_linea AS n "
                + "FROM proyecto_linea pl JOIN linea_translocal lt ON lt.id_linea = pl.id_linea", rs -> {
            lineas.computeIfAbsent(rs.getString("p"), k -> new ArrayList<>()).add(rs.getString("n"));
        });

        Map<String, List<Integer>> ods = new HashMap<>();
        jdbc.query("SELECT po.id_proyecto AS p, o.numero_ods AS n "
                + "FROM proyecto_ods po JOIN ods o ON o.id_ods = po.id_ods", rs -> {
            Integer n = getInt(rs, "n");
            if (n != null) {
                ods.computeIfAbsent(rs.getString("p"), k -> new ArrayList<>()).add(n);
            }
        });

        List<ProyectoRow> out = new ArrayList<>();
        jdbc.query("SELECT * FROM proyecto", rs -> {
            String id = rs.getString("id_proyecto");
            out.add(new ProyectoRow(
                    getInt(rs, "anio"),
                    rs.getString("periodo"),
                    rs.getString("convocatoria"),
                    rs.getString("codigo_proyecto"),
                    rs.getString("nombre_proyecto"),
                    investigadores.get(principal.get(id)),
                    regionales.get(rs.getString("id_unidad_regional")),
                    facultades.get(rs.getString("id_facultad")),
                    programas.get(rs.getString("id_programa")),
                    rs.getString("tipo_investigacion"),
                    rs.getString("tiene_convenio"),
                    equipos.get(id),
                    lineas.getOrDefault(id, List.of()),
                    ods.getOrDefault(id, List.of()),
                    rs.getString("objetivo_general")
            ));
        });
        return out;
    }

    public GruposDashboard grupos() {
        Map<String, List<String>> programas = new HashMap<>();
        jdbc.query("SELECT gp.id_grupo AS g, pg.nombre_programa_grupo AS n FROM grupo_programa gp "
                + "JOIN programa_grupo pg ON pg.id_programa_grupo = gp.id_programa_grupo", rs -> {
            programas.computeIfAbsent(rs.getString("g"), k -> new ArrayList<>()).add(rs.getString("n"));
        });

        List<GrupoInfo> grupos = new ArrayList<>();
        jdbc.query("SELECT g.id_grupo, g.nombre_grupo, g.lider_grupo, g.facultad_referencia, g.sede_referencia, "
                + "fg.nombre_facultad_grupo, ug.nombre_unidad_regional_grupo "
                + "FROM grupo g "
                + "LEFT JOIN facultad_grupo fg ON fg.id_facultad_grupo = g.id_facultad_grupo "
                + "LEFT JOIN unidad_regional_grupo ug ON ug.id_unidad_regional_grupo = g.id_unidad_regional_grupo", rs -> {
            String id = rs.getString("id_grupo");
            grupos.add(new GrupoInfo(
                    id,
                    rs.getString("nombre_grupo"),
                    rs.getString("nombre_grupo"),
                    rs.getString("lider_grupo"),
                    texto(rs.getString("nombre_facultad_grupo"), rs.getString("facultad_referencia")),
                    texto(rs.getString("nombre_unidad_regional_grupo"), rs.getString("sede_referencia")),
                    programas.getOrDefault(id, List.of())
            ));
        });

        Map<String, Integer> anios = new HashMap<>();
        Map<String, String> convocatorias = new HashMap<>();
        jdbc.query("SELECT id_proyecto, anio, convocatoria FROM proyecto", rs -> {
            anios.put(rs.getString("id_proyecto"), getInt(rs, "anio"));
            convocatorias.put(rs.getString("id_proyecto"), rs.getString("convocatoria"));
        });

        Map<String, List<ParticipanteGrupo>> participantes = new HashMap<>();
        jdbc.query("SELECT pa.id_proyecto AS p, pa.id_grupo AS g, i.nombre_investigador AS n, pa.rol AS r "
                + "FROM participacion pa LEFT JOIN investigador i ON i.id_investigador = pa.id_investigador", rs -> {
            participantes.computeIfAbsent(rs.getString("p"), k -> new ArrayList<>())
                    .add(new ParticipanteGrupo(rs.getString("g"), rs.getString("n"), rs.getString("r")));
        });

        Map<String, List<GrupoEnProyecto>> gruposProyecto = new HashMap<>();
        jdbc.query("SELECT id_proyecto AS p, id_grupo AS g, rol_grupo_proyecto AS r FROM proyecto_grupo", rs -> {
            gruposProyecto.computeIfAbsent(rs.getString("p"), k -> new ArrayList<>())
                    .add(new GrupoEnProyecto(rs.getString("g"), rs.getString("r")));
        });

        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(participantes.keySet());
        ids.addAll(gruposProyecto.keySet());
        List<GrupoProyecto> proyectos = new ArrayList<>();
        for (String id : ids) {
            proyectos.add(new GrupoProyecto(
                    id,
                    anios.get(id),
                    convocatorias.get(id),
                    participantes.getOrDefault(id, List.of()),
                    gruposProyecto.getOrDefault(id, List.of())
            ));
        }

        List<ColaboracionGrupo> colaboraciones = new ArrayList<>();
        jdbc.query("SELECT id_proyecto, id_grupo_origen, nombre_grupo_origen, id_grupo_destino, nombre_grupo_destino "
                + "FROM proyecto_colaboracion_grupo", rs -> {
            colaboraciones.add(new ColaboracionGrupo(
                    rs.getString("id_proyecto"),
                    rs.getString("id_grupo_origen"),
                    rs.getString("nombre_grupo_origen"),
                    rs.getString("id_grupo_destino"),
                    rs.getString("nombre_grupo_destino")
            ));
        });

        return new GruposDashboard(grupos, proyectos, colaboraciones);
    }

    private Map<String, String> mapa(String sql) {
        Map<String, String> m = new HashMap<>();
        jdbc.query(sql, rs -> {
            m.put(rs.getString(1), rs.getString(2));
        });
        return m;
    }

    private Integer getInt(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private String texto(String preferido, String alterno) {
        return (preferido == null || preferido.isBlank()) ? alterno : preferido;
    }
}
