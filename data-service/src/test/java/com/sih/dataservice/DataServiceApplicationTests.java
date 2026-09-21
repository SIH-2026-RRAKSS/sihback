package com.sih.dataservice;

import com.sih.dataservice.audit.repository.AuditLogRepository;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DataServiceApplicationTests {

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private BankRepository bankRepository;

    @MockBean
    private JurisdictionRepository jurisdictionRepository;

    @MockBean
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }

}
