package com.sih.dataservice.seed;

import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @PersistenceContext
    private EntityManager entityManager;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            seedUsers();
        }
    }

    private void seedUsers() {
        // Seed dummy bank
        UUID bankId = UUID.randomUUID();
        entityManager.createNativeQuery("INSERT INTO banks (id, code, name, active) VALUES (:id, 'SBI', 'State Bank of India', true)")
                .setParameter("id", bankId)
                .executeUpdate();

        // Seed dummy jurisdiction
        UUID jurId = UUID.randomUUID();
        entityManager.createNativeQuery("INSERT INTO jurisdictions (id, level, name, path) VALUES (:id, 'STATION', 'Central Delhi', 'DL/CENTRAL')")
                .setParameter("id", jurId)
                .executeUpdate();

        com.sih.dataservice.users.entity.Bank bankEntity = entityManager.find(com.sih.dataservice.users.entity.Bank.class, bankId);
        com.sih.dataservice.users.entity.Jurisdiction jurEntity = entityManager.find(com.sih.dataservice.users.entity.Jurisdiction.class, jurId);

        // CYBER001
        User cyber = new User();
        cyber.setRole(UserRole.CYBER_OFFICER);
        cyber.setName("Cyber Officer");
        cyber.setEmployeeId("CYBER001");
        cyber.setPasswordHash(passwordEncoder.encode("OfficerPassword123!"));
        cyber.setStatus(UserStatus.ACTIVE);
        cyber.setTokenVersion(1);
        userRepository.save(cyber);

        // POLICE001
        User police = new User();
        police.setRole(UserRole.POLICE);
        police.setName("Police SHO");
        police.setEmployeeId("POLICE001");
        police.setPasswordHash(passwordEncoder.encode("PolicePassword123!"));
        police.setStatus(UserStatus.ACTIVE);
        police.setTokenVersion(1);
        police.setJurisdiction(jurEntity);
        userRepository.save(police);

        // BANK001
        User bankUser = new User();
        bankUser.setRole(UserRole.BANK_MANAGER);
        bankUser.setName("Bank Nodal Mgr");
        bankUser.setEmployeeId("BANK001");
        bankUser.setPasswordHash(passwordEncoder.encode("BankPassword123!"));
        bankUser.setStatus(UserStatus.ACTIVE);
        bankUser.setTokenVersion(1);
        bankUser.setBank(bankEntity);
        userRepository.save(bankUser);

        // ADMIN001
        User admin = new User();
        admin.setRole(UserRole.ADMIN);
        admin.setName("System Admin");
        admin.setEmployeeId("ADMIN001");
        admin.setPasswordHash(passwordEncoder.encode("AdminPassword123!"));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTokenVersion(1);
        userRepository.save(admin);
        
        System.out.println("=========================================");
        System.out.println("Test users have been seeded successfully!");
        System.out.println("=========================================");
    }
}
