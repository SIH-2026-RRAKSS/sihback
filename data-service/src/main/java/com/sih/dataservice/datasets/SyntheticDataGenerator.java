package com.sih.dataservice.datasets;

import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.complaints.entity.FinancialEntity;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.entity.TransactionSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Component
public class SyntheticDataGenerator {

    /**
     * Generates a realistic synthetic transaction graph with planted money-mule structures (FR-GRA-1).
     *
     * @param numLegitAccounts Count of normal background accounts
     * @param numMuleRings     Count of planted money mule topologies
     * @param baseTime         Start reference time
     * @return Generated transactions with groundTruthLabel and connected financial entities
     */
    public List<Transaction> generateSyntheticDataset(int numLegitAccounts, int numMuleRings, Instant baseTime) {
        List<Transaction> dataset = new ArrayList<>();
        List<FinancialEntity> legitEntities = new ArrayList<>();

        // Create legitimate background entities
        for (int i = 0; i < numLegitAccounts; i++) {
            String hash = String.format("legit_hash_%04d_%s", i, UUID.randomUUID().toString().substring(0, 8));
            FinancialEntity entity = new FinancialEntity(hash, "enc_" + hash, null, EntityType.ACCOUNT);
            entity.setId(UUID.randomUUID());
            legitEntities.add(entity);
        }

        // 1. Generate normal background transactions
        Random random = new Random(42); // Deterministic seed for reproducible benchmarks
        for (int i = 0; i < numLegitAccounts * 2; i++) {
            FinancialEntity sender = legitEntities.get(random.nextInt(legitEntities.size()));
            FinancialEntity receiver = legitEntities.get(random.nextInt(legitEntities.size()));
            if (sender.getId().equals(receiver.getId())) continue;

            double amt = 500 + random.nextDouble() * 4500; // Normal amounts between 500 and 5000
            Instant txTime = baseTime.plusSeconds(random.nextInt(86400 * 7)); // Spread across 7 days
            String utr = "UTR-LEGIT-" + UUID.randomUUID().toString().substring(0, 12);

            Transaction tx = new Transaction(
                    utr, sender, receiver,
                    BigDecimal.valueOf(amt).setScale(2, RoundingMode.HALF_UP),
                    txTime, TransactionSource.DATASET
            );
            tx.setId(UUID.randomUUID());
            tx.setGroundTruthLabel("LEGITIMATE");
            dataset.add(tx);
        }

        // 2. Generate planted Money Mule Rings (Fan-Out -> Layering -> Fan-In)
        for (int r = 0; r < numMuleRings; r++) {
            FinancialEntity victim = new FinancialEntity(
                    "victim_hash_" + r, "enc_victim_" + r, null, EntityType.ACCOUNT);
            victim.setId(UUID.randomUUID());

            FinancialEntity cashOutMule = new FinancialEntity(
                    "cashout_hash_" + r, "enc_cashout_" + r, null, EntityType.ACCOUNT);
            cashOutMule.setId(UUID.randomUUID());

            // Layering accounts (fan-out layer)
            int layerCount = 3;
            List<FinancialEntity> layerEntities = new ArrayList<>();
            for (int l = 0; l < layerCount; l++) {
                FinancialEntity layerMule = new FinancialEntity(
                        String.format("mule_layer_%d_%d", r, l), "enc_mule_" + r + "_" + l, null, EntityType.UPI);
                layerMule.setId(UUID.randomUUID());
                layerEntities.add(layerMule);
            }

            Instant incidentTime = baseTime.plusSeconds(86400 * 2 + r * 3600);

            // Plant Fan-out: Victim -> Layering accounts (rapid dispersal)
            double initialStolen = 90000.0 + r * 10000.0;
            double splitAmount = initialStolen / layerCount;

            for (int l = 0; l < layerCount; l++) {
                Instant t = incidentTime.plusSeconds(l * 60 + 10);
                String utr = String.format("UTR-MULE-FO-%d-%d", r, l);
                Transaction tx = new Transaction(
                        utr, victim, layerEntities.get(l),
                        BigDecimal.valueOf(splitAmount).setScale(2, RoundingMode.HALF_UP),
                        t, TransactionSource.DATASET
                );
                tx.setId(UUID.randomUUID());
                tx.setGroundTruthLabel("MULE_FAN_OUT");
                dataset.add(tx);
            }

            // Plant Fan-in / Consolidation: Layering accounts -> Cashout mule
            for (int l = 0; l < layerCount; l++) {
                Instant t = incidentTime.plusSeconds(1800 + l * 120); // 30 mins later
                String utr = String.format("UTR-MULE-FI-%d-%d", r, l);
                Transaction tx = new Transaction(
                        utr, layerEntities.get(l), cashOutMule,
                        BigDecimal.valueOf(splitAmount * 0.95).setScale(2, RoundingMode.HALF_UP), // slight fee cut
                        t, TransactionSource.DATASET
                );
                tx.setId(UUID.randomUUID());
                tx.setGroundTruthLabel("MULE_FAN_IN");
                dataset.add(tx);
            }
        }

        dataset.sort(Comparator.comparing(Transaction::getTimestamp));
        return dataset;
    }
}
