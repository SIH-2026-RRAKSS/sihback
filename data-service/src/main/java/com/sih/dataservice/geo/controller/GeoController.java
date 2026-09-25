package com.sih.dataservice.geo.controller;

import com.sih.dataservice.geo.dto.EntityLocationDto;
import com.sih.dataservice.geo.dto.GeoCorridorDto;
import com.sih.dataservice.geo.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Geospatial & Entity Locations", description = "Endpoints for entity GPS coordinates, ATM terminals, and active laundering corridors")
public class GeoController {

    private final GeoService geoService;

    public GeoController(GeoService geoService) {
        this.geoService = geoService;
    }

    @Operation(summary = "Get all entity and ATM terminal GPS coordinates for interactive map plotting")
    @GetMapping("/entities/locations")
    public ResponseEntity<List<EntityLocationDto>> getEntityLocations() {
        List<EntityLocationDto> locations = geoService.getEntityLocations();
        return ResponseEntity.ok(locations);
    }

    @Operation(summary = "Get top multi-hop geographic laundering corridors")
    @GetMapping("/legacy/geo/corridors")
    public ResponseEntity<List<GeoCorridorDto>> getGeoCorridors() {
        List<GeoCorridorDto> corridors = geoService.getGeoCorridors();
        return ResponseEntity.ok(corridors);
    }
}

