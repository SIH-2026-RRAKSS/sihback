package com.sih.dataservice.users.repository;

import com.sih.dataservice.users.entity.Jurisdiction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JurisdictionRepository extends JpaRepository<Jurisdiction, UUID> {
    Optional<Jurisdiction> findByPath(String path);
    List<Jurisdiction> findByPathStartingWith(String pathPrefix);
    List<Jurisdiction> findByParentId(UUID parentId);
    boolean existsByPath(String path);
}
