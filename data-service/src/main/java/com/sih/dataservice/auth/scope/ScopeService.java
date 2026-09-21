package com.sih.dataservice.auth.scope;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.users.entity.UserRole;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ScopeService {

    /**
     * Checks if the principal has access to a case/complaint based on their role and scope.
     * Scope isolation rules (design.md Section 5):
     * - COMPLAINANT: own complaints only
     * - BANK_EMPLOYEE / BANK_MANAGER: cases involving their bank
     * - POLICE: cases in their jurisdiction hierarchy (path prefix match)
     * - CYBER_OFFICER: all cases
     * - ADMIN: no case access
     */
    public boolean isCaseInScope(UserPrincipal principal, UUID caseComplainantId, UUID caseBankId, String caseJurisdictionPath) {
        if (principal == null) {
            return false;
        }

        UserRole role = principal.getRole();

        switch (role) {
            case CYBER_OFFICER:
                return true;

            case COMPLAINANT:
                return caseComplainantId != null && caseComplainantId.equals(principal.getId());

            case BANK_EMPLOYEE:
            case BANK_MANAGER:
                return principal.getBankId() != null && principal.getBankId().equals(caseBankId);

            case POLICE:
                if (principal.getJurisdictionPath() == null || caseJurisdictionPath == null) {
                    return false;
                }
                // Prefix match: a district officer at /OD/KHORDHA/ sees /OD/KHORDHA/STATION_A/
                return caseJurisdictionPath.startsWith(principal.getJurisdictionPath());

            case ADMIN:
            default:
                // Admins manage users and config, never case data
                return false;
        }
    }

    /**
     * Enforces case scope. Throws 404 NOT FOUND (NFR-SEC-1) instead of 403 Forbidden
     * to avoid leaking existence of out-of-scope resources.
     */
    public void enforceCaseAccess(UserPrincipal principal, UUID caseComplainantId, UUID caseBankId, String caseJurisdictionPath) {
        if (!isCaseInScope(principal, caseComplainantId, caseBankId, caseJurisdictionPath)) {
            throw ApiException.notFound("Resource not found");
        }
    }

    /**
     * Checks if a graph node (bank account / entity) is within the caller's scope for masking.
     * Nodes outside scope will be masked to hashed ID and risk score only (FR-GRA-3).
     */
    public boolean isNodeInScope(UserPrincipal principal, UUID nodeBankId, String nodeJurisdictionPath) {
        if (principal == null) {
            return false;
        }

        UserRole role = principal.getRole();

        switch (role) {
            case CYBER_OFFICER:
                return true;

            case BANK_EMPLOYEE:
            case BANK_MANAGER:
                return principal.getBankId() != null && principal.getBankId().equals(nodeBankId);

            case POLICE:
                if (principal.getJurisdictionPath() == null || nodeJurisdictionPath == null) {
                    return false;
                }
                return nodeJurisdictionPath.startsWith(principal.getJurisdictionPath());

            case COMPLAINANT:
            case ADMIN:
            default:
                return false;
        }
    }
}
