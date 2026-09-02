package com.laboratory.auth.domain.model.GateWay;

import com.laboratory.auth.domain.model.Usuario;

public interface UsuarioGateWay {

    Usuario guardarUsuario(Usuario usuario);
    Usuario buscarUsuarioId(String idcard);
    Usuario actualizarUsuario(String idcard,Usuario usuario);
    void eliminarUsuario(String idcard);


}
