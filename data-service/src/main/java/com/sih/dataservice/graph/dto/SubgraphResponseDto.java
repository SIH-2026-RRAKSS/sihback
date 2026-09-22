package com.sih.dataservice.graph.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SubgraphResponseDto {

    private UUID rootEntityId;
    private int nodeCount;
    private int edgeCount;
    private List<GraphNodeDto> nodes = new ArrayList<>();
    private List<GraphEdgeDto> edges = new ArrayList<>();

    public SubgraphResponseDto() {
    }

    public SubgraphResponseDto(UUID rootEntityId, List<GraphNodeDto> nodes, List<GraphEdgeDto> edges) {
        this.rootEntityId = rootEntityId;
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.edges = edges != null ? edges : new ArrayList<>();
        this.nodeCount = this.nodes.size();
        this.edgeCount = this.edges.size();
    }

    public UUID getRootEntityId() {
        return rootEntityId;
    }

    public void setRootEntityId(UUID rootEntityId) {
        this.rootEntityId = rootEntityId;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public void setEdgeCount(int edgeCount) {
        this.edgeCount = edgeCount;
    }

    public List<GraphNodeDto> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNodeDto> nodes) {
        this.nodes = nodes;
        this.nodeCount = nodes != null ? nodes.size() : 0;
    }

    public List<GraphEdgeDto> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphEdgeDto> edges) {
        this.edges = edges;
        this.edgeCount = edges != null ? edges.size() : 0;
    }
}
