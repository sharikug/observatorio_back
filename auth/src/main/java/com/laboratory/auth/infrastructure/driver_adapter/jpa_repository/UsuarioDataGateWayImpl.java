package com.laboratory.auth.infrastructure.driver_adapter.jpa_repository;

import com.laboratory.auth.domain.model.GateWay.UsuarioGateWay;
import com.laboratory.auth.domain.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UsuarioDataGateWayImpl implements UsuarioGateWay {

    private final UsuarioDataJpaRepository repository;

    @Override
    public Usuario guardarUsuario(Usuario usuario) {
        return null;
    }

    @Override
    public Usuario buscarUsuarioId(String idcard) {
        return null;
    }

    @Override
    public Usuario actualizarUsuario(String idcard, Usuario usuario) {
        return null;
    }

    @Override
    public void eliminarUsuario(String idcard) {

    }
}
