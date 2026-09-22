package com.sih.dataservice.graph.engine;

import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.complaints.entity.FinancialEntity;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.entity.TransactionSource;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.graph.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemporalGraphEngineTest {

    @Mock
    private TransactionRepository transactionRepository;

    private Clock fixedClock;
    private TemporalGraphEngine engine;

    private FinancialEntity nodeA;
    private FinancialEntity nodeB;
    private FinancialEntity nodeC;
    private FinancialEntity nodeD;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
        engine = new TemporalGraphEngine(transactionRepository, fixedClock, 30);

        nodeA = new FinancialEntity("hashA", "encA", null, EntityType.ACCOUNT);
        nodeA.setId(UUID.randomUUID());

        nodeB = new FinancialEntity("hashB", "encB", null, EntityType.ACCOUNT);
        nodeB.setId(UUID.randomUUID());

        nodeC = new FinancialEntity("hashC", "encC", null, EntityType.UPI);
        nodeC.setId(UUID.randomUUID());

        nodeD = new FinancialEntity("hashD", "encD", null, EntityType.WALLET);
        nodeD.setId(UUID.randomUUID());
    }

    @Test
    void addsTransactionsAndConstructsDirectedEdges() {
        Instant t1 = Instant.parse("2026-09-22T00:01:00Z");
        Transaction tx1 = new Transaction("UTR01", nodeA, nodeB, new BigDecimal("1000.00"), t1, TransactionSource.COMPLAINT);
        tx1.setId(UUID.randomUUID());

        engine.addTransaction(tx1);

        assertThat(engine.getNodeCount()).isEqualTo(2);
        assertThat(engine.getEdgeCount()).isEqualTo(1);
    }

    @Test
    void boundedKhopExtractionReturnsExpectedDepthAndNeighborhood() {
        // Topology: A -> B -> C -> D
        Instant t1 = Instant.parse("2026-09-22T00:01:00Z");
        Instant t2 = Instant.parse("2026-09-22T00:02:00Z");
        Instant t3 = Instant.parse("2026-09-22T00:03:00Z");

        Transaction tx1 = new Transaction("UTR01", nodeA, nodeB, new BigDecimal("1000.00"), t1, TransactionSource.COMPLAINT);
        tx1.setId(UUID.randomUUID());
        Transaction tx2 = new Transaction("UTR02", nodeB, nodeC, new BigDecimal("900.00"), t2, TransactionSource.COMPLAINT);
        tx2.setId(UUID.randomUUID());
        Transaction tx3 = new Transaction("UTR03", nodeC, nodeD, new BigDecimal("800.00"), t3, TransactionSource.COMPLAINT);
        tx3.setId(UUID.randomUUID());

        engine.addTransactions(List.of(tx1, tx2, tx3));

        // 1-hop extraction from A
        Subgraph hop1 = engine.extractSubgraph(nodeA.getId(), 1, null, null, 10);
        assertThat(hop1.getNodes()).containsOnlyKeys(nodeA.getId(), nodeB.getId());
        assertThat(hop1.getNodeDepths().get(nodeA.getId())).isEqualTo(0);
        assertThat(hop1.getNodeDepths().get(nodeB.getId())).isEqualTo(1);
        assertThat(hop1.getEdges()).hasSize(1);

        // 2-hop extraction from A
        Subgraph hop2 = engine.extractSubgraph(nodeA.getId(), 2, null, null, 10);
        assertThat(hop2.getNodes()).containsOnlyKeys(nodeA.getId(), nodeB.getId(), nodeC.getId());
        assertThat(hop2.getNodeDepths().get(nodeC.getId())).isEqualTo(2);
        assertThat(hop2.getEdges()).hasSize(2);

        // Max nodes limitation (limit to 2 nodes even if 3 hops requested)
        Subgraph limited = engine.extractSubgraph(nodeA.getId(), 3, null, null, 2);
        assertThat(limited.getNodes()).hasSize(2);
    }

    @Test
    void timeWindowFiltersOutOldTransactions() {
        Instant tOld = Instant.parse("2026-09-01T00:00:00Z");
        Instant tNew = Instant.parse("2026-09-22T00:00:00Z");

        Transaction txOld = new Transaction("UTR_OLD", nodeA, nodeB, new BigDecimal("500.00"), tOld, TransactionSource.COMPLAINT);
        txOld.setId(UUID.randomUUID());
        Transaction txNew = new Transaction("UTR_NEW", nodeA, nodeC, new BigDecimal("700.00"), tNew, TransactionSource.COMPLAINT);
        txNew.setId(UUID.randomUUID());

        engine.addTransactions(List.of(txOld, txNew));

        Instant windowStart = Instant.parse("2026-09-15T00:00:00Z");
        Subgraph subgraph = engine.extractSubgraph(nodeA.getId(), 2, windowStart, null, 10);

        assertThat(subgraph.getNodes()).containsOnlyKeys(nodeA.getId(), nodeC.getId());
        assertThat(subgraph.getEdges()).hasSize(1);
        assertThat(subgraph.getEdges().get(0).getUtr()).isEqualTo("UTR_NEW");
    }
}
