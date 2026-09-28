package com.laboratory.auth.observatorio.ia.api.dto;

import java.util.List;

public record BorradorRequest(String tema, List<String> filtros) {
}
