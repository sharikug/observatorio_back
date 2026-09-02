package com.laboratory.auth.aplication;

import com.laboratory.auth.domain.model.GateWay.UsuarioGateWay;
import com.laboratory.auth.domain.usecase.UsuarioUsecase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    public UsuarioUsecase usuarioUsecase(UsuarioGateWay usuarioGateWay){
        return new UsuarioUsecase(usuarioGateWay);
    }
}
