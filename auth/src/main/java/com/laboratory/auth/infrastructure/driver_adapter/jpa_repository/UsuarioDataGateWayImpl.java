package com.laboratory.auth.infrastructure.driver_adapter.jpa_repository;

import com.laboratory.auth.domain.model.GateWay.UsuarioGateWay;
import com.laboratory.auth.domain.model.Usuario;
import com.laboratory.auth.infrastructure.mapper.UsuarioMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UsuarioDataGateWayImpl implements UsuarioGateWay {

    private final UsuarioDataJpaRepository repository;
    private final UsuarioMapper usuarioMapper;

    @Override
    public Usuario guardarUsuario(Usuario usuario) {
        UsuarioData data = repository.save(usuarioMapper.toUsuarioData(usuario));
        return usuarioMapper.toUsuario(data);
    }

    @Override
    public Usuario buscarUsuarioId(String idcard) {
        return repository.findByIdcard(idcard).map(usuarioMapper::toUsuario).orElse(null);
    }

    @Override
    public Usuario buscarUsuarioEmail(String email) {
        return repository.findByEmail(email).map(usuarioMapper::toUsuario).orElse(null);
    }

    @Override
    public boolean existeUsuarioEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public boolean existeUsuarioIdcard(String idcard) {
        return repository.existsByIdcard(idcard);
    }

    @Override
    public Usuario actualizarUsuario(String idcard, Usuario usuario) {
        UsuarioData data = repository.findByIdcard(idcard).orElse(null);
        if (data == null) {
            return null;
        }
        data.setName(usuario.getName());
        data.setLastname(usuario.getLastname());
        data.setEmail(usuario.getEmail());
        data.setPassword(usuario.getPassword());
        data.setPhone(usuario.getPhone());
        data.setRol(usuario.getRol());
        data.setState(usuario.getState());
        return usuarioMapper.toUsuario(repository.save(data));
    }

    @Override
    public void eliminarUsuario(String idcard) {
        repository.findByIdcard(idcard).ifPresent(repository::delete);
    }
}