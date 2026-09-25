import pytest
from app.synthetic.mule_generator import SyntheticMuleGenerator


def test_generate_fan_out():
    gen = SyntheticMuleGenerator(seed=123)
    graph = gen.generate_fan_out("test-fan-out", num_mules=4)
    assert graph.label == 1
    assert graph.pattern_type == "FAN_OUT"
    assert graph.root_node_id == "node-0"
    assert len(graph.mule_node_ids) == 4
    # Root node has out-edges to all 4 mules
    out_edges = [e for e in graph.edges if e.source == "node-0" and e.target in graph.mule_node_ids]
    assert len(out_edges) == 4
    for e in out_edges:
        assert e.amount > 0


def test_generate_fan_in():
    gen = SyntheticMuleGenerator(seed=123)
    graph = gen.generate_fan_in("test-fan-in", num_sources=5)
    assert graph.label == 1
    assert graph.pattern_type == "FAN_IN"
    assert graph.root_node_id == "node-0"
    assert len(graph.mule_node_ids) == 5
    in_edges = [e for e in graph.edges if e.target == "node-0" and e.source in graph.mule_node_ids]
    assert len(in_edges) == 5


def test_generate_layering():
    gen = SyntheticMuleGenerator(seed=123)
    graph = gen.generate_layering("test-layering", chain_length=4)
    assert graph.label == 1
    assert graph.pattern_type == "LAYERING"
    assert len(graph.nodes) == 4
    assert len(graph.edges) == 3


def test_generate_normal():
    gen = SyntheticMuleGenerator(seed=123)
    graph = gen.generate_normal("test-normal", num_nodes=5, num_edges=6)
    assert graph.label == 0
    assert graph.pattern_type == "NORMAL"
    assert len(graph.mule_node_ids) == 0
    assert len(graph.nodes) == 5
    assert len(graph.edges) == 6


def test_generate_dataset():
    gen = SyntheticMuleGenerator(seed=42)
    dataset = gen.generate_dataset(num_samples=20, fraud_ratio=0.4)
    assert len(dataset) == 20
    fraud_count = sum(1 for g in dataset if g.label == 1)
    normal_count = sum(1 for g in dataset if g.label == 0)
    assert fraud_count == 8
    assert normal_count == 12
