package com.laboratory.auth.infrastructure.driver_adapter.rest;

import com.laboratory.auth.domain.model.Rol;
import com.laboratory.auth.domain.model.Usuario;
import com.laboratory.auth.domain.usecase.UsuarioUsecase;
import com.laboratory.auth.infrastructure.driver_adapter.rest.dto.AuthResponse;
import com.laboratory.auth.infrastructure.driver_adapter.rest.dto.LoginRequest;
import com.laboratory.auth.infrastructure.driver_adapter.rest.dto.RegistroRequest;
import com.laboratory.auth.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Codigo que convierte un registro en ADMINISTRADOR. Vive solo en el backend: el
     * cliente lo manda, nunca lo decide. Si se deja vacio, la API no tiene forma de
     * crear administradores y todos los registros quedan como ESTUDIANTE.
     */
    @Value("${app.registro.codigo-admin:}")
    private String codigoAdmin;

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@RequestBody RegistroRequest request) {
        // La longitud se valida aqui, con el texto plano: mas abajo el caso ya solo
        // conoce el hash, que siempre mide 60 caracteres y nunca falla la regla.
        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException("La contrasena debe tener al menos 8 caracteres");
        }
        Usuario usuario = Usuario.builder()
                .idcard(request.idcard())
                .name(request.name())
                .lastname(request.lastname())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .rol(resolverRol(request.codigoAdmin()))
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

    /**
     * Sin el codigo correcto el registro es siempre de estudiante. Un codigo en blanco
     * o un backend sin codigo configurado nunca conceden el rol de administrador.
     */
    private String resolverRol(String codigo) {
        boolean esAdmin = codigoAdmin != null && !codigoAdmin.isBlank()
                && codigoAdmin.equals(codigo);
        return esAdmin ? Rol.ADMINISTRADOR : Rol.ESTUDIANTE;
    }
}