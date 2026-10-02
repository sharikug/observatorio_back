package com.laboratory.auth.domain.model.GateWay;

import com.laboratory.auth.domain.model.Usuario;

public interface UsuarioGateWay {

    Usuario guardarUsuario(Usuario usuario);
    Usuario buscarUsuarioEmail(String email);
    boolean existeUsuarioEmail(String email);
    boolean existeUsuarioIdcard(String idcard);
}