package com.sih.dataservice.graph.engine;

import com.sih.dataservice.complaints.entity.FinancialEntity;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.model.GraphEdge;
import com.sih.dataservice.graph.model.GraphNode;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.graph.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class TemporalGraphEngine {

    private static final Logger log = LoggerFactory.getLogger(TemporalGraphEngine.class);

    private final Map<UUID, GraphNode> nodes = new HashMap<>();
    private final Map<UUID, List<GraphEdge>> outgoing = new HashMap<>();
    private final Map<UUID, List<GraphEdge>> incoming = new HashMap<>();
    private final Set<UUID> edgeTransactionIds = new HashSet<>();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final int initialLoadDays;

    public TemporalGraphEngine(
            TransactionRepository transactionRepository,
            Clock clock,
            @Value("${graph.preload.days:30}") int initialLoadDays) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.initialLoadDays = initialLoadDays;
    }

    @PostConstruct
    public void init() {
        rwLock.writeLock().lock();
        try {
            Instant cutoff = Instant.now(clock).minus(Duration.ofDays(initialLoadDays));
            List<Transaction> recentTransactions = transactionRepository.findTransactionsSince(cutoff);
            for (Transaction tx : recentTransactions) {
                addTransactionInternal(tx);
            }
            log.info("Initialized TemporalGraphEngine with {} nodes and {} edges (preload window={} days)",
                    nodes.size(), edgeTransactionIds.size(), initialLoadDays);
        } catch (Exception e) {
            log.warn("Could not preload graph from database: {}", e.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Adds a transaction to the in-memory directed multigraph under write-lock (FR-GRA-1).
     */
    public void addTransaction(Transaction tx) {
        if (tx == null || tx.getSender() == null || tx.getReceiver() == null) {
            return;
        }
        rwLock.writeLock().lock();
        try {
            addTransactionInternal(tx);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Batch adds transactions under a single write-lock acquisition.
     */
    public void addTransactions(Collection<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        rwLock.writeLock().lock();
        try {
            for (Transaction tx : transactions) {
                addTransactionInternal(tx);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void addTransactionInternal(Transaction tx) {
        if (tx.getId() != null && edgeTransactionIds.contains(tx.getId())) {
            return; // Idempotent
        }

        FinancialEntity sender = tx.getSender();
        FinancialEntity receiver = tx.getReceiver();
        Instant txTime = tx.getTimestamp();

        // Ensure nodes exist and update timestamps
        GraphNode senderNode = nodes.computeIfAbsent(sender.getId(), id ->
                new GraphNode(
                        sender.getId(),
                        sender.getAccountHash(),
                        sender.getBank() != null ? sender.getBank().getId() : null,
                        sender.getType(),
                        txTime
                ));
        senderNode.updateSeen(txTime);

        GraphNode receiverNode = nodes.computeIfAbsent(receiver.getId(), id ->
                new GraphNode(
                        receiver.getId(),
                        receiver.getAccountHash(),
                        receiver.getBank() != null ? receiver.getBank().getId() : null,
                        receiver.getType(),
                        txTime
                ));
        receiverNode.updateSeen(txTime);

        GraphEdge edge = new GraphEdge(
                tx.getId(),
                tx.getUtr(),
                sender.getId(),
                receiver.getId(),
                tx.getAmount(),
                txTime
        );

        outgoing.computeIfAbsent(sender.getId(), k -> new ArrayList<>()).add(edge);
        incoming.computeIfAbsent(receiver.getId(), k -> new ArrayList<>()).add(edge);
        if (tx.getId() != null) {
            edgeTransactionIds.add(tx.getId());
        }
    }

    /**
     * Bounded K-hop subgraph extraction (FR-GRA-2):
     * Explores from rootEntityId up to maxHops within [startTime, endTime], bounded by maxNodes.
     */
    public Subgraph extractSubgraph(UUID rootEntityId, int maxHops, Instant startTime, Instant endTime, int maxNodes) {
        rwLock.readLock().lock();
        try {
            if (!nodes.containsKey(rootEntityId)) {
                return new Subgraph(rootEntityId, Map.of(), Map.of(), List.of());
            }

            int nodeLimit = Math.max(1, maxNodes);
            int hopLimit = Math.max(0, maxHops);

            Map<UUID, Integer> nodeDepths = new HashMap<>();
            Map<UUID, GraphNode> visitedNodes = new HashMap<>();
            Set<GraphEdge> collectedEdges = new LinkedHashSet<>();
            Queue<UUID> queue = new ArrayDeque<>();

            // Initialize with root node
            visitedNodes.put(rootEntityId, nodes.get(rootEntityId));
            nodeDepths.put(rootEntityId, 0);
            queue.add(rootEntityId);

            while (!queue.isEmpty() && visitedNodes.size() < nodeLimit) {
                UUID current = queue.poll();
                int currentDepth = nodeDepths.get(current);

                if (currentDepth >= hopLimit) {
                    continue; // Do not expand neighbors beyond maxHops
                }

                // Expand outgoing neighbors (forward flow)
                List<GraphEdge> outEdges = outgoing.getOrDefault(current, List.of());
                for (GraphEdge edge : outEdges) {
                    if (isWithinTimeWindow(edge.getTimestamp(), startTime, endTime)) {
                        UUID neighbor = edge.getTargetEntityId();
                        if (!visitedNodes.containsKey(neighbor)) {
                            if (visitedNodes.size() >= nodeLimit) {
                                break;
                            }
                            visitedNodes.put(neighbor, nodes.get(neighbor));
                            nodeDepths.put(neighbor, currentDepth + 1);
                            queue.add(neighbor);
                        }
                        collectedEdges.add(edge);
                    }
                }

                // Expand incoming neighbors (reverse flow)
                List<GraphEdge> inEdges = incoming.getOrDefault(current, List.of());
                for (GraphEdge edge : inEdges) {
                    if (isWithinTimeWindow(edge.getTimestamp(), startTime, endTime)) {
                        UUID neighbor = edge.getSourceEntityId();
                        if (!visitedNodes.containsKey(neighbor)) {
                            if (visitedNodes.size() >= nodeLimit) {
                                break;
                            }
                            visitedNodes.put(neighbor, nodes.get(neighbor));
                            nodeDepths.put(neighbor, currentDepth + 1);
                            queue.add(neighbor);
                        }
                        collectedEdges.add(edge);
                    }
                }
            }

            // Also collect cross-edges between visited nodes that match the time window
            for (UUID visitedId : visitedNodes.keySet()) {
                List<GraphEdge> outEdges = outgoing.getOrDefault(visitedId, List.of());
                for (GraphEdge edge : outEdges) {
                    if (visitedNodes.containsKey(edge.getTargetEntityId()) &&
                            isWithinTimeWindow(edge.getTimestamp(), startTime, endTime)) {
                        collectedEdges.add(edge);
                    }
                }
            }

            return new Subgraph(rootEntityId, nodeDepths, visitedNodes, new ArrayList<>(collectedEdges));

        } finally {
            rwLock.readLock().unlock();
        }
    }

    private boolean isWithinTimeWindow(Instant timestamp, Instant start, Instant end) {
        if (timestamp == null) return false;
        if (start != null && timestamp.isBefore(start)) return false;
        if (end != null && timestamp.isAfter(end)) return false;
        return true;
    }

    public int getNodeCount() {
        rwLock.readLock().lock();
        try {
            return nodes.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getEdgeCount() {
        rwLock.readLock().lock();
        try {
            return edgeTransactionIds.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
