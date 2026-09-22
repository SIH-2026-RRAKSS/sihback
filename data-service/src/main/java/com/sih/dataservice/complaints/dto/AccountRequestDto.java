package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.AccountRole;
import com.sih.dataservice.complaints.entity.EntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AccountRequestDto {

    @NotBlank
    private String accountNumber;

    @NotNull
    private EntityType entityType;

    @NotNull
    private AccountRole role;

    private UUID bankId;
    private String ifsc;

    public AccountRequestDto() {
    }

    public AccountRequestDto(String accountNumber, EntityType entityType, AccountRole role, UUID bankId, String ifsc) {
        this.accountNumber = accountNumber;
        this.entityType = entityType;
        this.role = role;
        this.bankId = bankId;
        this.ifsc = ifsc;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
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

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }
}
