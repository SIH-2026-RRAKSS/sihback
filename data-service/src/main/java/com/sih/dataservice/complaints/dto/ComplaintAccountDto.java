package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.AccountRole;
import com.sih.dataservice.complaints.entity.ComplaintAccount;
import com.sih.dataservice.complaints.entity.EntityType;
import java.util.UUID;

public class ComplaintAccountDto {

    private UUID id;
    private UUID entityId;
    private EntityType entityType;
    private AccountRole role;
    private UUID bankId;
    private String bankName;
    private String accountHash;

    public ComplaintAccountDto() {
    }

    public static ComplaintAccountDto fromEntity(ComplaintAccount ca) {
        ComplaintAccountDto dto = new ComplaintAccountDto();
        dto.setId(ca.getId());
        if (ca.getEntity() != null) {
            dto.setEntityId(ca.getEntity().getId());
            dto.setEntityType(ca.getEntity().getType());
            dto.setAccountHash(ca.getEntity().getAccountHash());
            if (ca.getEntity().getBank() != null) {
                dto.setBankId(ca.getEntity().getBank().getId());
                dto.setBankName(ca.getEntity().getBank().getName());
            }
        }
        dto.setRole(ca.getRole());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }

    public UUID getBankId() {
        return bankId;
    }

    public void setBankId(UUID bankId) {
        this.bankId = bankId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountHash() {
        return accountHash;
    }

    public void setAccountHash(String accountHash) {
        this.accountHash = accountHash;
    }
}
