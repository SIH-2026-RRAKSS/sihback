package com.sih.dataservice.seed;

import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.Jurisdiction;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, BankRepository bankRepository, JurisdictionRepository jurisdictionRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bankRepository = bankRepository;
        this.jurisdictionRepository = jurisdictionRepository;
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
        Bank bankEntity = new Bank("SBI", "State Bank of India", true);
        bankEntity = bankRepository.save(bankEntity);

        // Seed dummy jurisdiction
        Jurisdiction jurEntity = new Jurisdiction();
        jurEntity.setLevel("STATION");
        jurEntity.setName("Central Delhi");
        jurEntity.setPath("DL/CENTRAL");
        jurEntity = jurisdictionRepository.save(jurEntity);

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
