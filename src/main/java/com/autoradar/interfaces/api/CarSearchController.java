package com.autoradar.interfaces.api;

import com.autoradar.application.service.AggregatorService;
import com.autoradar.domain.dto.CarDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
@Tag(name = "Busca de Carros", description = "Endpoints para agregar buscas de veículos")
public class CarSearchController {

    private final AggregatorService service;

    public CarSearchController(AggregatorService service) {
        this.service = service;
    }

    @Operation(summary = "Buscar em todos os sites", description = "Aciona todos os scrapers configurados e retorna uma lista unificada e ordenada.")
    @GetMapping("/search")
    public List<CarDTO> search(
            @RequestParam(defaultValue = "Civic") String query,
            @RequestParam(defaultValue = "SP") String location,
            @RequestParam(required = false) Double maxPrice // Novo parâmetro opcional
    ) {
        return service.searchAll(query, location, maxPrice);
    }
}