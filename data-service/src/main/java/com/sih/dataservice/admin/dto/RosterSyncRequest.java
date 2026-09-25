package com.sih.dataservice.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RosterSyncRequest(
        @NotEmpty(message = "Roster entries list cannot be empty")
        List<@Valid RosterEntryDto> entries
) {
}
