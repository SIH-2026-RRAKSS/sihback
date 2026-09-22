package com.sih.dataservice.graph.service;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.graph.dto.GraphEdgeDto;
import com.sih.dataservice.graph.dto.GraphNodeDto;
import com.sih.dataservice.graph.dto.SubgraphResponseDto;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.model.GraphEdge;
import com.sih.dataservice.graph.model.GraphNode;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.BankRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GraphService {

    private final TemporalGraphEngine graphEngine;
    private final ScopeService scopeService;
    private final BankRepository bankRepository;
    private final com.sih.dataservice.complaints.repository.ComplaintRepository complaintRepository;
    private final com.sih.dataservice.complaints.repository.ComplaintAccountRepository complaintAccountRepository;

    private final Map<UUID, String> bankNameCache = new ConcurrentHashMap<>();

    public GraphService(
            TemporalGraphEngine graphEngine,
            ScopeService scopeService,
            BankRepository bankRepository,
            com.sih.dataservice.complaints.repository.ComplaintRepository complaintRepository,
            com.sih.dataservice.complaints.repository.ComplaintAccountRepository complaintAccountRepository) {
        this.graphEngine = graphEngine;
        this.scopeService = scopeService;
        this.bankRepository = bankRepository;
        this.complaintRepository = complaintRepository;
        this.complaintAccountRepository = complaintAccountRepository;
    }

    /**
     * Extracts and masks the transaction graph for an incident (FR-GRA-2, FR-GRA-3).
     */
    public SubgraphResponseDto getIncidentGraph(UUID complaintId, int hops, int maxNodes, UserPrincipal principal) {
        com.sih.dataservice.complaints.entity.Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        String path = complaint.getJurisdiction() != null ? complaint.getJurisdiction().getPath() : null;
        scopeService.enforceCaseAccess(principal, complaint.getComplainant().getId(), null, path);

        List<com.sih.dataservice.complaints.entity.ComplaintAccount> accounts =
                complaintAccountRepository.findByComplaintId(complaintId);
        if (accounts.isEmpty()) {
            return new SubgraphResponseDto(null, List.of(), List.of());
        }

        UUID rootEntityId = accounts.get(0).getEntity().getId();
        return getKhopSubgraph(rootEntityId, hops, null, null, maxNodes, principal);
    }

    /**
     * Extracts a bounded K-hop subgraph around rootEntityId and applies caller scope masking (FR-GRA-2, FR-GRA-3).
     */
    public SubgraphResponseDto getKhopSubgraph(
            UUID rootEntityId,
            int maxHops,
            Instant startTime,
            Instant endTime,
            int maxNodes,
            UserPrincipal principal) {

        if (principal == null || principal.getRole() == UserRole.COMPLAINANT || principal.getRole() == UserRole.ADMIN) {
            throw ApiException.forbidden("Complainants and admins cannot query internal transaction subgraphs");
        }

        int hops = Math.min(Math.max(1, maxHops), 5); // bounded between 1 and 5 hops
        int limit = Math.min(Math.max(1, maxNodes), 200); // maximum 200 nodes per extraction

        Subgraph rawSubgraph = graphEngine.extractSubgraph(rootEntityId, hops, startTime, endTime, limit);

        List<GraphNodeDto> nodeDtos = new ArrayList<>();
        Map<UUID, Integer> depths = rawSubgraph.getNodeDepths();

        for (GraphNode node : rawSubgraph.getNodes().values()) {
            GraphNodeDto dto = new GraphNodeDto();
            dto.setEntityId(node.getEntityId());
            dto.setAccountHash(node.getAccountHash());
            dto.setRiskScore(node.getRiskScore());
            dto.setHopDepth(depths.getOrDefault(node.getEntityId(), 0));

            // Scope masking check (FR-GRA-3, NFR-SEC-1)
            boolean inScope = scopeService.isNodeInScope(principal, node.getBankId(), null);

            if (inScope) {
                dto.setMasked(false);
                dto.setBankId(node.getBankId());
                if (node.getBankId() != null) {
                    dto.setBankName(getBankName(node.getBankId()));
                }
                dto.setType(node.getType());
                dto.setFirstSeen(node.getFirstSeen());
                dto.setLastSeen(node.getLastSeen());
            } else {
                // Masked node: only hashed id and risk score, no bank or temporal details
                dto.setMasked(true);
                dto.setBankId(null);
                dto.setBankName(null);
                dto.setType(null);
                dto.setFirstSeen(null);
                dto.setLastSeen(null);
            }
            nodeDtos.add(dto);
        }

        List<GraphEdgeDto> edgeDtos = new ArrayList<>();
        for (GraphEdge edge : rawSubgraph.getEdges()) {
            edgeDtos.add(new GraphEdgeDto(
                    edge.getTransactionId(),
                    edge.getUtr(),
                    edge.getSourceEntityId(),
                    edge.getTargetEntityId(),
                    edge.getAmount(),
                    edge.getTimestamp()
            ));
        }

        return new SubgraphResponseDto(rootEntityId, nodeDtos, edgeDtos);
    }

    private String getBankName(UUID bankId) {
        return bankNameCache.computeIfAbsent(bankId, id ->
                bankRepository.findById(id).map(Bank::getName).orElse(null));
    }
}
