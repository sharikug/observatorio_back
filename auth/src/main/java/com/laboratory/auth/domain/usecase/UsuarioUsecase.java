package com.laboratory.auth.domain.usecase;

import com.laboratory.auth.domain.model.GateWay.UsuarioGateWay;
import com.laboratory.auth.domain.model.Usuario;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor

public class UsuarioUsecase {

    private final UsuarioGateWay usuarioGateWay;


    public Usuario guardarUsuario (Usuario usuario){

        if (usuario.getIdcard() == null){
            throw new NullPointerException("La cedula no puede estar vacia");
        }
        if (usuario.getEmail() == null){
            throw new NullPointerException("El correo no puede estar vacia");
        }

        return usuarioGateWay.guardarUsuario(usuario);
    }

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
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()){
            throw new IllegalArgumentException("La contrasena no puede estar vacia");
        }
        if (usuarioGateWay.existeUsuarioEmail(usuario.getEmail())){
            throw new IllegalArgumentException("El correo ya esta registrado");
        }
        if (usuarioGateWay.existeUsuarioIdcard(usuario.getIdcard())){
            throw new IllegalArgumentException("La cedula ya esta registrada");
        }
        if (usuario.getRol() == null || usuario.getRol().isBlank()){
            usuario.setRol("ESTUDIANTE");
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