package com.sih.dataservice.complaints.entity;

public enum PublicStatus {
    RECEIVED("Received"),
    UNDER_REVIEW("Under review"),
    ACTION_TAKEN("Action taken"),
    CLOSED("Closed");

    private final String displayName;

    PublicStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Maps the 8 internal investigation statuses to the 4 simplified complainant public statuses (FR-STAT-2).
     */
    public static PublicStatus fromInternalStatus(ComplaintStatus internalStatus) {
        if (internalStatus == null) {
            return RECEIVED;
        }

        return switch (internalStatus) {
            case FILED -> RECEIVED;
            case TRIAGED, ASSIGNED, UNDER_INVESTIGATION -> UNDER_REVIEW;
            case FREEZE_REQUESTED, BANK_RESPONDED -> ACTION_TAKEN;
            case CLOSED_FRAUD, CLOSED_NOT_FRAUD -> CLOSED;
        };
    }
}
