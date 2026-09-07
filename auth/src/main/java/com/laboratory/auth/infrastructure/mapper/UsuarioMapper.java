package com.laboratory.auth.infrastructure.mapper;

import com.laboratory.auth.domain.model.Usuario;
import com.laboratory.auth.infrastructure.driver_adapter.jpa_repository.UsuarioData;
import org.springframework.stereotype.Component;

@Component

public class UsuarioMapper {
    public Usuario toUsuario(UsuarioData usuarioData) {
        return new Usuario(
                usuarioData.getId(),
                usuarioData.getIdcard(),
                usuarioData.getName(),
                usuarioData.getLastname(),
                usuarioData.getEmail(),
                usuarioData.getPassword(),
                usuarioData.getPhone(),
                usuarioData.getRol(),
                usuarioData.getState()
        );
    }

    public UsuarioData toUsuarioData(Usuario usuario){
        return new UsuarioData(
                usuario.getId(),
                usuario.getIdcard(),
                usuario.getName(),
                usuario.getLastname(),
                usuario.getEmail(),
                usuario.getPassword(),
                usuario.getPhone(),
                usuario.getRol(),
                usuario.getState()
        );
    }
}