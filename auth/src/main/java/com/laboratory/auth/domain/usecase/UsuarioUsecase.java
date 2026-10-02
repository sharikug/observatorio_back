package com.laboratory.auth.domain.usecase;

import com.laboratory.auth.domain.model.GateWay.UsuarioGateWay;
import com.laboratory.auth.domain.model.Rol;
import com.laboratory.auth.domain.model.Usuario;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor

public class UsuarioUsecase {

    private final UsuarioGateWay usuarioGateWay;

    public Usuario registrarUsuario(Usuario usuario){
        if (usuario.getIdcard() == null || usuario.getIdcard().isBlank()){
            throw new IllegalArgumentException("La cedula no puede estar vacia");
        }
        if (usuario.getEmail() == null || usuario.getEmail().isBlank()){
            throw new IllegalArgumentException("El correo no puede estar vacio");
        }
        if (usuario.getName() == null || usuario.getName().isBlank()){
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
        // El caso ya recibe el hash, no la contrasena: solo puede comprobar que
        // hubo alguna. La regla de longitud la aplica el endpoint, sobre el texto plano.
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()){
            throw new IllegalArgumentException("La contrasena no puede estar vacia");
        }
        if (usuarioGateWay.existeUsuarioEmail(usuario.getEmail())){
            throw new IllegalArgumentException("El correo ya esta registrado");
        }
        if (usuarioGateWay.existeUsuarioIdcard(usuario.getIdcard())){
            throw new IllegalArgumentException("La cedula ya esta registrada");
        }
        // Ultima linea de defensa del rol: el endpoint ya solo produce ADMINISTRADOR o
        // ESTUDIANTE, y aqui se rechaza cualquier otro valor, venga de donde venga.
        if (!Rol.esValido(usuario.getRol())){
            throw new IllegalArgumentException("El rol no es valido");
        }
        if (usuario.getState() == null || usuario.getState().isBlank()){
            usuario.setState("ACTIVO");
        }
        return usuarioGateWay.guardarUsuario(usuario);
    }

    public Usuario buscarUsuarioEmail(String email){
        return usuarioGateWay.buscarUsuarioEmail(email);
    }
}