import pytest
from app.features.extractor import FeatureExtractor
from app.synthetic.mule_generator import SyntheticMuleGenerator


def test_feature_extraction_from_synthetic_graph():
    gen = SyntheticMuleGenerator(seed=42)
    graph = gen.generate_fan_out("graph-fo", num_mules=4)
    extractor = FeatureExtractor()

    dict_graph = graph.to_dict()
    node_feats = extractor.extract_node_features(
        nodes=dict_graph["nodes"],
        edges=dict_graph["edges"],
        root_node_id=dict_graph["root_node_id"],
    )

    assert "node-0" in node_feats
    root_f = node_feats["node-0"]
    assert root_f["out_degree"] == 4.0
    assert root_f["fan_out_ratio"] > 0.0
    assert root_f["in_degree"] >= 0.0
    assert root_f["hop_depth"] == 0.0

    # Mule node checks
    mule_f = node_feats["node-1"]
    assert mule_f["in_degree"] == 1.0
    assert mule_f["fan_in_ratio"] == 1.0
    assert mule_f["hop_depth"] == 1.0

    graph_feats = extractor.extract_graph_features(
        nodes=dict_graph["nodes"],
        edges=dict_graph["edges"],
        root_node_id=dict_graph["root_node_id"],
    )
    assert graph_feats["num_nodes"] >= 5
    assert graph_feats["num_edges"] == len(dict_graph["edges"])
    assert graph_feats["total_volume"] > 0
    assert graph_feats["fan_out_ratio"] > 0


def test_matrix_and_vector_conversion():
    extractor = FeatureExtractor()
    nodes = [{"id": "node-0"}, {"id": "node-1"}]
    edges = [{"source": "node-0", "target": "node-1", "amount": 1000.0, "timestamp": "2026-09-20T10:00:00Z"}]

    node_feats = extractor.extract_node_features(nodes, edges)
    mat = extractor.node_features_to_matrix(node_feats, ["node-0", "node-1"])
    assert mat.shape == (2, len(extractor.node_feature_names))

    graph_feats = extractor.extract_graph_features(nodes, edges)
    vec = extractor.graph_features_to_vector(graph_feats)
    assert vec.shape == (len(extractor.graph_feature_names),)
