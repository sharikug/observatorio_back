package com.laboratory.auth.infrastructure.driver_adapter.rest;

import com.laboratory.auth.domain.model.Usuario;
import com.laboratory.auth.domain.usecase.UsuarioUsecase;
import com.laboratory.auth.infrastructure.driver_adapter.rest.dto.AuthResponse;
import com.laboratory.auth.infrastructure.driver_adapter.rest.dto.LoginRequest;
import com.laboratory.auth.infrastructure.driver_adapter.rest.dto.MessageResponse;
import com.laboratory.auth.infrastructure.driver_adapter.rest.dto.RegistroRequest;
import com.laboratory.auth.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioUsecase usuarioUsecase;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@RequestBody RegistroRequest request) {
        Usuario usuario = Usuario.builder()
                .idcard(request.idcard())
                .name(request.name())
                .lastname(request.lastname())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .rol(request.rol())
                .build();

        Usuario guardado = usuarioUsecase.registrarUsuario(usuario);
        String token = jwtService.generateToken(guardado);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AuthResponse(token, guardado.getName(), guardado.getRol()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        Usuario usuario = usuarioUsecase.buscarUsuarioEmail(request.email());
        if (usuario == null || !passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new BadCredentialsException("Credenciales invalidas");
        }
        if (!"ACTIVO".equalsIgnoreCase(usuario.getState())) {
            throw new BadCredentialsException("El usuario esta inactivo");
        }
        String token = jwtService.generateToken(usuario);
        return ResponseEntity.ok(
                new AuthResponse(token, usuario.getName(), usuario.getRol()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout() {
        return ResponseEntity.ok(new MessageResponse("Sesion cerrada"));
    }
}