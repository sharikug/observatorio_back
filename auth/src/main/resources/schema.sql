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
