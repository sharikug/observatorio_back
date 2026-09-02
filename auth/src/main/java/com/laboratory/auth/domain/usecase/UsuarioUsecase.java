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
}
