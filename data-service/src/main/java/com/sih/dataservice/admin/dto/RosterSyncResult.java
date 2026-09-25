package com.sih.dataservice.admin.dto;

public record RosterSyncResult(
        int totalProcessed,
        int createdCount,
        int updatedCount,
        int deactivatedCount
) {
}
