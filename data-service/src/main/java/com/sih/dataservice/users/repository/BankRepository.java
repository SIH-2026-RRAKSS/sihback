package com.sih.dataservice.users.repository;

import com.sih.dataservice.users.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankRepository extends JpaRepository<Bank, UUID> {
    Optional<Bank> findByCode(String code);
    List<Bank> findByActiveTrue();
    boolean existsByCode(String code);
}
