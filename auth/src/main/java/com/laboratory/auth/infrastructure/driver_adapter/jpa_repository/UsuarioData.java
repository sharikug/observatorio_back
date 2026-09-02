package com.laboratory.auth.infrastructure.driver_adapter.jpa_repository;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String idcard;
    private String name;
    private String lastname;
    private String email;
    private String username;
    private String password;
    private Integer age;
    private String phone;
    private String rol;
    private String state;

}
