package com.laboratory.auth.infrastructure.driver_adapter.jpa_repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioDataJpaRepository extends JpaRepository<UsuarioData,Long> {
    Optional<UsuarioData> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByIdcard(String idcard);
}