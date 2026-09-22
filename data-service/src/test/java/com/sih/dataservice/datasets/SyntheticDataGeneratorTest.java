package com.sih.dataservice.datasets;

import com.sih.dataservice.graph.entity.Transaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticDataGeneratorTest {

    @Test
    void generatesSyntheticDatasetWithPlantedMulesAndNormalTraffic() {
        SyntheticDataGenerator generator = new SyntheticDataGenerator();
        Instant baseTime = Instant.parse("2026-09-01T00:00:00Z");

        List<Transaction> dataset = generator.generateSyntheticDataset(10, 2, baseTime);

        assertThat(dataset).isNotEmpty();

        long fanOutCount = dataset.stream().filter(t -> "MULE_FAN_OUT".equals(t.getGroundTruthLabel())).count();
        long fanInCount = dataset.stream().filter(t -> "MULE_FAN_IN".equals(t.getGroundTruthLabel())).count();
        long legitCount = dataset.stream().filter(t -> "LEGITIMATE".equals(t.getGroundTruthLabel())).count();

        // 2 rings * 3 layer nodes = 6 fan-out and 6 fan-in
        assertThat(fanOutCount).isEqualTo(6);
        assertThat(fanInCount).isEqualTo(6);
        assertThat(legitCount).isGreaterThan(0);

        // Verify all transactions have valid amounts and non-null entities
        for (Transaction tx : dataset) {
            assertThat(tx.getAmount()).isPositive();
            assertThat(tx.getSender()).isNotNull();
            assertThat(tx.getReceiver()).isNotNull();
            assertThat(tx.getUtr()).isNotBlank();
        }
    }
}
