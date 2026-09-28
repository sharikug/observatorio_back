CREATE TABLE IF NOT EXISTS facultad (
    id_facultad TEXT PRIMARY KEY,
    nombre_facultad TEXT
);

CREATE TABLE IF NOT EXISTS programa (
    id_programa TEXT PRIMARY KEY,
    nombre_programa TEXT
);

CREATE TABLE IF NOT EXISTS unidad_regional (
    id_unidad_regional TEXT PRIMARY KEY,
    nombre_unidad_regional TEXT
);

CREATE TABLE IF NOT EXISTS linea_translocal (
    id_linea TEXT PRIMARY KEY,
    nombre_linea TEXT
);

CREATE TABLE IF NOT EXISTS ods (
    id_ods TEXT PRIMARY KEY,
    numero_ods INTEGER,
    nombre_ods TEXT
);

CREATE TABLE IF NOT EXISTS investigador (
    id_investigador TEXT PRIMARY KEY,
    nombre_investigador TEXT,
    id_unidad_regional TEXT,
    id_facultad TEXT,
    id_programa TEXT
);

CREATE TABLE IF NOT EXISTS grupo (
    id_grupo TEXT PRIMARY KEY,
    nombre_grupo TEXT,
    categoria_minciencias TEXT,
    lider_grupo TEXT,
    facultad_referencia TEXT,
    sede_referencia TEXT,
    id_facultad_grupo TEXT,
    id_unidad_regional_grupo TEXT
);

CREATE TABLE IF NOT EXISTS facultad_grupo (
    id_facultad_grupo TEXT PRIMARY KEY,
    nombre_facultad_grupo TEXT
);

CREATE TABLE IF NOT EXISTS unidad_regional_grupo (
    id_unidad_regional_grupo TEXT PRIMARY KEY,
    nombre_unidad_regional_grupo TEXT
);

CREATE TABLE IF NOT EXISTS programa_grupo (
    id_programa_grupo TEXT PRIMARY KEY,
    nombre_programa_grupo TEXT
);

CREATE TABLE IF NOT EXISTS proyecto (
    id_proyecto TEXT PRIMARY KEY,
    codigo_proyecto TEXT UNIQUE,
    nombre_proyecto TEXT,
    convocatoria TEXT,
    anio INTEGER,
    periodo TEXT,
    estado_proyecto TEXT,
    tipo_investigacion TEXT,
    id_unidad_regional TEXT,
    id_facultad TEXT,
    id_programa TEXT,
    porcentaje_avance_tecnico TEXT,
    tiene_convenio TEXT,
    objetivo_general TEXT,
    objetivos_especificos TEXT,
    palabras_clave TEXT,
    origen TEXT
);

CREATE TABLE IF NOT EXISTS participacion (
    id_participacion TEXT PRIMARY KEY,
    id_proyecto TEXT,
    id_investigador TEXT,
    id_grupo TEXT,
    rol TEXT,
    orden_participacion INTEGER
);

CREATE TABLE IF NOT EXISTS proyecto_equipo (
    id_proyecto TEXT PRIMARY KEY,
    id_investigador_principal TEXT,
    tamano_equipo INTEGER
);

CREATE TABLE IF NOT EXISTS proyecto_linea (
    id BIGSERIAL PRIMARY KEY,
    id_proyecto TEXT,
    id_linea TEXT,
    UNIQUE (id_proyecto, id_linea)
);

CREATE TABLE IF NOT EXISTS proyecto_ods (
    id BIGSERIAL PRIMARY KEY,
    id_proyecto TEXT,
    id_ods TEXT,
    UNIQUE (id_proyecto, id_ods)
);

CREATE TABLE IF NOT EXISTS proyecto_grupo (
    id BIGSERIAL PRIMARY KEY,
    id_proyecto TEXT,
    id_grupo TEXT,
    rol_grupo_proyecto TEXT,
    UNIQUE (id_proyecto, id_grupo)
);

CREATE TABLE IF NOT EXISTS grupo_programa (
    id BIGSERIAL PRIMARY KEY,
    id_grupo TEXT,
    id_programa_grupo TEXT,
    UNIQUE (id_grupo, id_programa_grupo)
);

CREATE TABLE IF NOT EXISTS proyecto_colaboracion_grupo (
    id_colaboracion TEXT PRIMARY KEY,
    id_proyecto TEXT,
    id_grupo_origen TEXT,
    nombre_grupo_origen TEXT,
    id_grupo_destino TEXT,
    nombre_grupo_destino TEXT
);

CREATE INDEX IF NOT EXISTS idx_participacion_proyecto ON participacion (id_proyecto);
CREATE INDEX IF NOT EXISTS idx_participacion_grupo ON participacion (id_grupo);
CREATE INDEX IF NOT EXISTS idx_proyecto_grupo_proyecto ON proyecto_grupo (id_proyecto);
CREATE INDEX IF NOT EXISTS idx_proyecto_grupo_grupo ON proyecto_grupo (id_grupo);

CREATE TABLE IF NOT EXISTS reporte_generado (
    id_reporte TEXT PRIMARY KEY,
    email TEXT,
    titulo TEXT,
    tipo TEXT,
    filtros TEXT,
    fecha_generacion TIMESTAMP,
    nombre_archivo TEXT,
    archivo BYTEA
);

CREATE INDEX IF NOT EXISTS idx_reporte_generado_email ON reporte_generado (email);

-- Modulo de IA -----------------------------------------------------------------
-- HU-06: documentos cargados al asistente (RAG). roles_permitidos es CSV de roles
-- autorizados; PUBLICO es visible para cualquier usuario autenticado.
CREATE TABLE IF NOT EXISTS ia_documento (
    id_documento TEXT PRIMARY KEY,
    nombre TEXT,
    tipo TEXT,
    fuente TEXT,
    autorizado BOOLEAN DEFAULT TRUE,
    roles_permitidos TEXT,
    estado TEXT,
    fecha_carga TIMESTAMP
);

-- HU-04/05/06: fragmentos indexados. El embedding se guarda como JSON de flotantes.
-- ponytail: similitud coseno en Java (O(n)); migrar a pgvector si el corpus crece.
CREATE TABLE IF NOT EXISTS ia_fragmento (
    id_fragmento BIGSERIAL PRIMARY KEY,
    id_documento TEXT,
    orden INTEGER,
    contenido TEXT,
    referencia TEXT,
    embedding TEXT,
    roles_permitidos TEXT
);

CREATE INDEX IF NOT EXISTS idx_ia_fragmento_documento ON ia_fragmento (id_documento);

-- HU-10: registro de trazabilidad de cada interaccion con el asistente.
CREATE TABLE IF NOT EXISTS ia_auditoria (
    id_auditoria BIGSERIAL PRIMARY KEY,
    email TEXT,
    rol TEXT,
    pregunta TEXT,
    fragmentos TEXT,
    sql_generado TEXT,
    respuesta TEXT,
    en_alcance BOOLEAN,
    latencia_ms BIGINT,
    fecha TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ia_auditoria_fecha ON ia_auditoria (fecha);

-- HU-04: diccionario de indicadores versionado como fuente autorizada.
CREATE TABLE IF NOT EXISTS ia_indicador (
    id_indicador TEXT PRIMARY KEY,
    nombre TEXT,
    definicion TEXT,
    formula TEXT,
    unidad TEXT,
    fuente TEXT,
    enlace TEXT,
    version INTEGER
);

-- Excel activo e historial ------------------------------------------------------
-- Solo el Excel ACTIVO alimenta dashboards e IA. Los anteriores se conservan aqui
-- (contenido BYTEA) para poder consultarlos y descargarlos, nunca para analizarlos.
CREATE TABLE IF NOT EXISTS excel_cargado (
    id_excel TEXT PRIMARY KEY,
    nombre TEXT,
    tamano BIGINT,
    contenido BYTEA,
    estado TEXT,
    fecha_carga TIMESTAMP,
    fecha_historico TIMESTAMP,
    usuario TEXT,
    valido BOOLEAN,
    criticas INTEGER,
    advertencias INTEGER,
    total_inconsistencias INTEGER,
    detalle_validacion TEXT
);

-- Regla fundamental: la base de datos, no la aplicacion, garantiza un unico ACTIVO.
-- Dos cargas simultaneas no pueden dejar dos filas en ACTIVO.
CREATE UNIQUE INDEX IF NOT EXISTS idx_excel_cargado_unico_activo
    ON excel_cargado (estado) WHERE estado = 'ACTIVO';

CREATE INDEX IF NOT EXISTS idx_excel_cargado_fecha ON excel_cargado (fecha_carga DESC);
