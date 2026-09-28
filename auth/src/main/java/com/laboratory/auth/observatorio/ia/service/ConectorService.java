package com.laboratory.auth.observatorio.ia.service;

import com.laboratory.auth.observatorio.ia.conector.ConectorFuente;
import com.laboratory.auth.observatorio.ia.config.IaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HU-12: los conectores se activan por configuracion (ia.conectores.activos)
 * y quedan sujetos al control de permisos del asistente.
 */
@Service
@RequiredArgsConstructor
public class ConectorService {

    private final List<ConectorFuente> conectores;
    private final IaProperties properties;

    public List<ConectorFuente> activos() {
        List<String> habilitados = properties.getConectores().getActivos();
        return conectores.stream().filter(c -> habilitados.contains(c.id())).toList();
    }
}
