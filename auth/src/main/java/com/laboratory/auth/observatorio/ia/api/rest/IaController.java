package com.laboratory.auth.observatorio.ia.api.rest;

import com.laboratory.auth.observatorio.ia.api.dto.BorradorRequest;
import com.laboratory.auth.observatorio.ia.api.dto.BorradorResponse;
import com.laboratory.auth.observatorio.ia.api.dto.ChatRequest;
import com.laboratory.auth.observatorio.ia.api.dto.ChatResponse;
import com.laboratory.auth.observatorio.ia.service.ConectorService;
import com.laboratory.auth.observatorio.ia.service.IaChatService;
import com.laboratory.auth.observatorio.ia.service.IndicadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class IaController {

    private final IaChatService chatService;
    private final IndicadorService indicadorService;
    private final ConectorService conectorService;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request, Authentication authentication) {
        return chatService.responder(authentication.getName(), rol(authentication), request.pregunta());
    }

    @PostMapping("/reportes")
    public BorradorResponse borrador(@RequestBody BorradorRequest request,
                                     Authentication authentication) {
        return chatService.generarBorrador(authentication.getName(), rol(authentication), request);
    }

    @GetMapping("/indicadores")
    public List<Map<String, Object>> indicadores() {
        return indicadorService.listar();
    }

    @GetMapping("/conectores")
    public List<Map<String, String>> conectores() {
        return conectorService.activos().stream()
                .map(c -> Map.of("id", c.id(), "descripcion", c.descripcion()))
                .toList();
    }

    static String rol(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return "EXTERNO";
        }
        String autoridad = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).findFirst().orElse("EXTERNO");
        return autoridad.startsWith("ROLE_") ? autoridad.substring(5) : autoridad;
    }
}
