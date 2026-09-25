package com.sih.dataservice.geo;

import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.common.config.SecurityConfig;
import com.sih.dataservice.geo.controller.GeoController;
import com.sih.dataservice.geo.dto.EntityLocationDto;
import com.sih.dataservice.geo.dto.GeoCorridorDto;
import com.sih.dataservice.geo.service.GeoService;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GeoController.class)
@Import(SecurityConfig.class)
class GeoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeoService geoService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /entities/locations should return entity locations list")
    void shouldReturnEntityLocations() throws Exception {
        EntityLocationDto loc = new EntityLocationDto(
                "ACC_1001",
                "ACCOUNT",
                "Rahul Sharma",
                "Mumbai",
                "Maharashtra",
                19.0760,
                72.8777,
                0.85,
                "HIGH",
                150000.0
        );
        when(geoService.getEntityLocations()).thenReturn(List.of(loc));

        mockMvc.perform(get("/entities/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity_id").value("ACC_1001"))
                .andExpect(jsonPath("$[0].city").value("Mumbai"))
                .andExpect(jsonPath("$[0].confidence_tier").value("HIGH"));
    }

    @Test
    @DisplayName("GET /geo/corridors should return list of corridors")
    void shouldReturnCorridors() throws Exception {
        GeoCorridorDto corridor = new GeoCorridorDto(
                "ACC_1001",
                "ATM_9001",
                "₹1,50,000",
                "Mumbai",
                List.of(19.0760, 72.8777),
                "Delhi",
                List.of(28.6139, 77.2090),
                "HIGH"
        );
        when(geoService.getGeoCorridors()).thenReturn(List.of(corridor));

        mockMvc.perform(get("/geo/corridors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromCity").value("Mumbai"))
                .andExpect(jsonPath("$[0].toCity").value("Delhi"))
                .andExpect(jsonPath("$[0].risk").value("HIGH"));
    }
}
