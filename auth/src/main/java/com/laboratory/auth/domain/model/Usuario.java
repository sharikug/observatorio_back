package com.laboratory.auth.domain.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class Usuario {

    private Long id;
    private String idcard;
    private String name;
    private String lastname;
    private String email;
    private String password;
    private String phone;
    private String rol;
    private String state;

}