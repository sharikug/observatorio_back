package com.laboratory.auth.domain.model;

/**
 * Los unicos dos roles que existen en el sistema.
 *
 * <p>El registro publico siempre concede ESTUDIANTE. ADMINISTRADOR queda a mano y
 * solo sale de ahi si la peticion trae el codigo secreto configurado en
 * {@code app.registro.codigo-admin}. Como el rol nunca lo decide el cliente, esta
 * lista es el unico lugar donde se puede agregar un rol nuevo.
 */
public final class Rol {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String ESTUDIANTE = "ESTUDIANTE";

    private Rol() {
    }

    public static boolean esValido(String rol) {
        return ADMINISTRADOR.equals(rol) || ESTUDIANTE.equals(rol);
    }
}