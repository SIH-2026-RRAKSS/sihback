package com.sih.dataservice.graph.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Subgraph {

    private final UUID rootEntityId;
    private final Map<UUID, Integer> nodeDepths; // entityId -> hop depth from root (0 = root)
    private final Map<UUID, GraphNode> nodes;
    private final List<GraphEdge> edges;

    public Subgraph(UUID rootEntityId, Map<UUID, Integer> nodeDepths, Map<UUID, GraphNode> nodes, List<GraphEdge> edges) {
        this.rootEntityId = rootEntityId;
        this.nodeDepths = nodeDepths;
        this.nodes = nodes;
        this.edges = edges;
    }

    public UUID getRootEntityId() {
        return rootEntityId;
    }

    public Map<UUID, Integer> getNodeDepths() {
        return nodeDepths;
    }

    public Map<UUID, GraphNode> getNodes() {
        return nodes;
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }
}
