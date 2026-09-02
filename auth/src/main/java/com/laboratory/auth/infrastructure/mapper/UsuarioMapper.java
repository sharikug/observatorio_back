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
                usuarioData.getPassword(),
                usuarioData.getEmail(),
                usuarioData.getPhone(),
                usuarioData.getAge(),
                usuarioData.getState(),
                usuarioData.getRol(),
                usuarioData.getUsername()

        );

    }

    public UsuarioData toUsuarioData(Usuario usuario){
        return new UsuarioData(
                usuario.getId(),
                usuario.getIdcard(),
                usuario.getName(),
                usuario.getLastname(),
                usuario.getPassword(),
                usuario.getEmail(),
                usuario.getPhone(),
                usuario.getAge(),
                usuario.getState(),
                usuario.getRol(),
                usuario.getUsername()


        );
    }
}
