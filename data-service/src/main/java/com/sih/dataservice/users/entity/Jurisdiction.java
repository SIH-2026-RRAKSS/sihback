package com.sih.dataservice.users.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jurisdictions")
public class Jurisdiction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Jurisdiction parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private JurisdictionLevel level;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "path", nullable = false, unique = true)
    private String path;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Jurisdiction() {
    }

    public Jurisdiction(Jurisdiction parent, JurisdictionLevel level, String name, String path) {
        this.parent = parent;
        this.level = level;
        this.name = name;
        this.path = path;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Jurisdiction getParent() {
        return parent;
    }

    public void setParent(Jurisdiction parent) {
        this.parent = parent;
    }

    public JurisdictionLevel getLevel() {
        return level;
    }

    public void setLevel(JurisdictionLevel level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
