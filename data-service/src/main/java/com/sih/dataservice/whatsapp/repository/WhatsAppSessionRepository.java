package com.sih.dataservice.whatsapp.repository;

import com.sih.dataservice.whatsapp.entity.WhatsAppSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, String> {
    Optional<WhatsAppSession> findByPhoneHash(String phoneHash);
}
