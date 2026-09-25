package com.sih.dataservice.admin.dto;

import com.sih.dataservice.users.entity.JurisdictionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateJurisdictionRequest(
        UUID parentId,

        @NotNull(message = "Jurisdiction level is required (STATE, DISTRICT, STATION)")
        JurisdictionLevel level,

        @NotBlank(message = "Jurisdiction name is required")
        String name,

        @NotBlank(message = "Materialized path is required (e.g. /OD/KHORDHA/)")
        String path
) {
}
