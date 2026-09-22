package com.sih.dataservice.graph.service;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.graph.dto.GraphNodeDto;
import com.sih.dataservice.graph.dto.SubgraphResponseDto;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.model.GraphNode;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.BankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private TemporalGraphEngine graphEngine;
    @Mock
    private ScopeService scopeService;
    @Mock
    private BankRepository bankRepository;

    private GraphService graphService;

    private UUID myBankId;
    private UUID otherBankId;
    private UUID entityInScope;
    private UUID entityOutOfScope;

    @BeforeEach
    void setUp() {
        graphService = new GraphService(graphEngine, scopeService, bankRepository);

        myBankId = UUID.randomUUID();
        otherBankId = UUID.randomUUID();

        entityInScope = UUID.randomUUID();
        entityOutOfScope = UUID.randomUUID();
    }

    @Test
    void rejectsComplainantOrAdminAccess() {
        UserPrincipal complainantPrincipal = new UserPrincipal(
                UUID.randomUUID(), "complainant", "pw", "Complainant",
                UserRole.COMPLAINANT, null, null, 1, com.sih.dataservice.users.entity.UserStatus.ACTIVE);

        assertThatThrownBy(() -> graphService.getKhopSubgraph(entityInScope, 2, null, null, 50, complainantPrincipal))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot query internal transaction subgraphs");
    }

    @Test
    void masksOutOfScopeNodesForBankEmployee() {
        UserPrincipal bankPrincipal = new UserPrincipal(
                UUID.randomUUID(), "emp", "pw", "Employee",
                UserRole.BANK_EMPLOYEE, myBankId, null, 1, com.sih.dataservice.users.entity.UserStatus.ACTIVE);

        Instant now = Instant.now();
        GraphNode node1 = new GraphNode(entityInScope, "hash_in_scope", myBankId, EntityType.ACCOUNT, now);
        GraphNode node2 = new GraphNode(entityOutOfScope, "hash_out_scope", otherBankId, EntityType.ACCOUNT, now);
        node2.setRiskScore(0.85);

        Subgraph mockSubgraph = new Subgraph(
                entityInScope,
                Map.of(entityInScope, 0, entityOutOfScope, 1),
                Map.of(entityInScope, node1, entityOutOfScope, node2),
                List.of()
        );

        when(graphEngine.extractSubgraph(eq(entityInScope), eq(2), any(), any(), eq(50)))
                .thenReturn(mockSubgraph);

        when(scopeService.isNodeInScope(bankPrincipal, myBankId, null)).thenReturn(true);
        when(scopeService.isNodeInScope(bankPrincipal, otherBankId, null)).thenReturn(false);

        Bank bank1 = new Bank();
        bank1.setId(myBankId);
        bank1.setName("HDFC Bank");
        when(bankRepository.findById(myBankId)).thenReturn(Optional.of(bank1));

        SubgraphResponseDto result = graphService.getKhopSubgraph(entityInScope, 2, null, null, 50, bankPrincipal);

        assertThat(result.getNodeCount()).isEqualTo(2);

        GraphNodeDto inScopeDto = result.getNodes().stream()
                .filter(n -> n.getEntityId().equals(entityInScope)).findFirst().orElseThrow();
        assertThat(inScopeDto.isMasked()).isFalse();
        assertThat(inScopeDto.getBankName()).isEqualTo("HDFC Bank");
        assertThat(inScopeDto.getType()).isEqualTo(EntityType.ACCOUNT);

        GraphNodeDto outOfScopeDto = result.getNodes().stream()
                .filter(n -> n.getEntityId().equals(entityOutOfScope)).findFirst().orElseThrow();
        assertThat(outOfScopeDto.isMasked()).isTrue();
        assertThat(outOfScopeDto.getBankId()).isNull();
        assertThat(outOfScopeDto.getBankName()).isNull();
        assertThat(outOfScopeDto.getType()).isNull();
        assertThat(outOfScopeDto.getAccountHash()).isEqualTo("hash_out_scope"); // Visible
        assertThat(outOfScopeDto.getRiskScore()).isEqualTo(0.85); // Visible
    }
}
