package com.sih.dataservice.whatsapp.entity;

import com.sih.dataservice.users.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "whatsapp_sessions")
public class WhatsAppSession {

    @Id
    @Column(name = "phone_hash", length = 64, nullable = false)
    private String phoneHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 30, nullable = false)
    private WhatsAppSessionState state = WhatsAppSessionState.LANGUAGE;

    @Column(name = "language", length = 10, nullable = false)
    private String language = "en";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft", columnDefinition = "jsonb")
    private String draft = "{}";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public WhatsAppSession() {
    }

    public WhatsAppSession(String phoneHash) {
        this.phoneHash = phoneHash;
        this.state = WhatsAppSessionState.LANGUAGE;
        this.language = "en";
        this.draft = "{}";
        this.updatedAt = Instant.now();
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public WhatsAppSessionState getState() {
        return state;
    }

    public void setState(WhatsAppSessionState state) {
        this.state = state;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDraft() {
        return draft;
    }

    public void setDraft(String draft) {
        this.draft = draft;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
