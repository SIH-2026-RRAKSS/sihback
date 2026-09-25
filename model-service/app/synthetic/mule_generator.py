"""Synthetic data generator with planted money mule rings for training and benchmarking.

Generates realistic transaction subgraphs with ground-truth labels:
1. Rapid Fan-Out (Dispersal): A single source disperses large funds to multiple mule accounts in a short time.
2. Rapid Fan-In (Consolidation): Multiple accounts funnel funds into a single collector account.
3. Multi-Hop Layering: A chain of accounts sequentially transferring funds to obfuscate origin.
4. Normal / Benign: Organic, balanced transactions with typical amounts and inter-arrival times.
"""

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Dict, List, Optional, Set, Tuple
import random
import numpy as np


@dataclass
class SyntheticNode:
    id: str
    is_mule: bool = False
    role: str = "NORMAL"  # SOURCE, MULE, EXIT, NORMAL


@dataclass
class SyntheticEdge:
    source: str
    target: str
    amount: float
    timestamp: datetime


@dataclass
class SyntheticGraph:
    graph_id: str
    nodes: List[SyntheticNode]
    edges: List[SyntheticEdge]
    label: int  # 1 = fraud/mule ring, 0 = benign
    pattern_type: str  # FAN_OUT, FAN_IN, LAYERING, NORMAL
    root_node_id: str
    mule_node_ids: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict:
        return {
            "graph_id": self.graph_id,
            "label": self.label,
            "pattern_type": self.pattern_type,
            "root_node_id": self.root_node_id,
            "mule_node_ids": self.mule_node_ids,
            "nodes": [{"id": n.id, "is_mule": n.is_mule, "role": n.role} for n in self.nodes],
            "edges": [
                {
                    "source": e.source,
                    "target": e.target,
                    "amount": round(e.amount, 2),
                    "timestamp": e.timestamp.isoformat(),
                }
                for e in self.edges
            ],
        }


class SyntheticMuleGenerator:
    """Generator for synthetic fraud and benign transaction subgraphs."""

    def __init__(self, seed: Optional[int] = 42):
        self.rng = random.Random(seed)
        self.np_rng = np.random.default_rng(seed)

    def generate_fan_out(
        self,
        graph_id: str,
        num_mules: Optional[int] = None,
        base_time: Optional[datetime] = None,
    ) -> SyntheticGraph:
        """Generate a rapid fan-out (dispersal) mule ring."""
        if num_mules is None:
            num_mules = self.rng.randint(3, 8)
        if base_time is None:
            base_time = datetime(2026, 9, 20, 10, 0, 0, tzinfo=timezone.utc)

        root_id = "node-0"
        nodes = [SyntheticNode(id=root_id, is_mule=True, role="SOURCE")]
        edges = []
        mule_node_ids = []

        total_amount = float(self.rng.uniform(50000.0, 500000.0))
        amounts = self.np_rng.dirichlet(np.ones(num_mules)) * total_amount

        curr_time = base_time
        for i in range(num_mules):
            mule_id = f"node-{i + 1}"
            nodes.append(SyntheticNode(id=mule_id, is_mule=True, role="MULE"))
            mule_node_ids.append(mule_id)

            # Rapid dispersal within 2 to 15 minutes intervals
            curr_time += timedelta(seconds=int(self.rng.uniform(60, 300)))
            edges.append(
                SyntheticEdge(
                    source=root_id,
                    target=mule_id,
                    amount=float(amounts[i]),
                    timestamp=curr_time,
                )
            )

        # Add 1-2 innocent peripheral background transactions
        extra_nodes_count = self.rng.randint(1, 3)
        for j in range(extra_nodes_count):
            extra_id = f"node-{num_mules + 1 + j}"
            nodes.append(SyntheticNode(id=extra_id, is_mule=False, role="NORMAL"))
            edges.append(
                SyntheticEdge(
                    source=extra_id,
                    target=root_id,
                    amount=float(self.rng.uniform(500.0, 3000.0)),
                    timestamp=base_time - timedelta(hours=int(self.rng.uniform(1, 24))),
                )
            )

        return SyntheticGraph(
            graph_id=graph_id,
            nodes=nodes,
            edges=edges,
            label=1,
            pattern_type="FAN_OUT",
            root_node_id=root_id,
            mule_node_ids=mule_node_ids,
        )

    def generate_fan_in(
        self,
        graph_id: str,
        num_sources: Optional[int] = None,
        base_time: Optional[datetime] = None,
    ) -> SyntheticGraph:
        """Generate a rapid fan-in (consolidation) mule ring."""
        if num_sources is None:
            num_sources = self.rng.randint(3, 8)
        if base_time is None:
            base_time = datetime(2026, 9, 20, 12, 0, 0, tzinfo=timezone.utc)

        root_id = "node-0"  # Target collector
        nodes = [SyntheticNode(id=root_id, is_mule=True, role="EXIT")]
        edges = []
        mule_node_ids = []

        curr_time = base_time
        for i in range(num_sources):
            src_id = f"node-{i + 1}"
            nodes.append(SyntheticNode(id=src_id, is_mule=True, role="MULE"))
            mule_node_ids.append(src_id)

            curr_time += timedelta(seconds=int(self.rng.uniform(45, 240)))
            amount = float(self.rng.uniform(10000.0, 80000.0))
            edges.append(
                SyntheticEdge(
                    source=src_id,
                    target=root_id,
                    amount=amount,
                    timestamp=curr_time,
                )
            )

        return SyntheticGraph(
            graph_id=graph_id,
            nodes=nodes,
            edges=edges,
            label=1,
            pattern_type="FAN_IN",
            root_node_id=root_id,
            mule_node_ids=mule_node_ids,
        )

    def generate_layering(
        self,
        graph_id: str,
        chain_length: Optional[int] = None,
        base_time: Optional[datetime] = None,
    ) -> SyntheticGraph:
        """Generate a multi-hop pass-through layering chain (A -> B -> C -> ...)."""
        if chain_length is None:
            chain_length = self.rng.randint(3, 6)
        if base_time is None:
            base_time = datetime(2026, 9, 20, 14, 0, 0, tzinfo=timezone.utc)

        nodes = []
        edges = []
        mule_node_ids = []

        curr_amount = float(self.rng.uniform(100000.0, 300000.0))
        curr_time = base_time

        for i in range(chain_length):
            node_id = f"node-{i}"
            role = "SOURCE" if i == 0 else ("EXIT" if i == chain_length - 1 else "MULE")
            nodes.append(SyntheticNode(id=node_id, is_mule=True, role=role))
            if i > 0:
                mule_node_ids.append(node_id)
                # Next transfer happens within 5 to 30 minutes, retaining 95-99% of funds
                curr_time += timedelta(seconds=int(self.rng.uniform(300, 1800)))
                curr_amount *= float(self.rng.uniform(0.95, 0.99))
                edges.append(
                    SyntheticEdge(
                        source=f"node-{i - 1}",
                        target=node_id,
                        amount=curr_amount,
                        timestamp=curr_time,
                    )
                )

        return SyntheticGraph(
            graph_id=graph_id,
            nodes=nodes,
            edges=edges,
            label=1,
            pattern_type="LAYERING",
            root_node_id="node-0",
            mule_node_ids=mule_node_ids,
        )

    def generate_normal(
        self,
        graph_id: str,
        num_nodes: Optional[int] = None,
        num_edges: Optional[int] = None,
        base_time: Optional[datetime] = None,
    ) -> SyntheticGraph:
        """Generate a benign/organic transaction subgraph."""
        if num_nodes is None:
            num_nodes = self.rng.randint(3, 7)
        if num_edges is None:
            num_edges = self.rng.randint(num_nodes - 1, num_nodes * 2)
        if base_time is None:
            base_time = datetime(2026, 9, 20, 9, 0, 0, tzinfo=timezone.utc)

        root_id = "node-0"
        nodes = [SyntheticNode(id=f"node-{i}", is_mule=False, role="NORMAL") for i in range(num_nodes)]
        edges = []

        # Connect nodes in organic fashion spread over days/hours
        for _ in range(num_edges):
            u = self.rng.choice(nodes).id
            v = self.rng.choice(nodes).id
            if u == v:
                v = nodes[(int(u.split("-")[1]) + 1) % num_nodes].id

            amount = float(self.np_rng.exponential(2500.0) + 100.0)
            tx_time = base_time + timedelta(
                days=int(self.rng.uniform(0, 7)),
                hours=int(self.rng.uniform(0, 24)),
                minutes=int(self.rng.uniform(0, 60)),
            )
            edges.append(SyntheticEdge(source=u, target=v, amount=amount, timestamp=tx_time))

        return SyntheticGraph(
            graph_id=graph_id,
            nodes=nodes,
            edges=edges,
            label=0,
            pattern_type="NORMAL",
            root_node_id=root_id,
            mule_node_ids=[],
        )

    def generate_dataset(
        self,
        num_samples: int = 100,
        fraud_ratio: float = 0.3,
    ) -> List[SyntheticGraph]:
        """Generate a balanced or imbalanced dataset of synthetic subgraphs."""
        dataset = []
        num_fraud = int(num_samples * fraud_ratio)
        num_normal = num_samples - num_fraud

        # Generate fraud graphs
        for i in range(num_fraud):
            gid = f"fraud-graph-{i}"
            choice = self.rng.choice(["fan_out", "fan_in", "layering"])
            if choice == "fan_out":
                g = self.generate_fan_out(gid)
            elif choice == "fan_in":
                g = self.generate_fan_in(gid)
            else:
                g = self.generate_layering(gid)
            dataset.append(g)

        # Generate benign graphs
        for j in range(num_normal):
            gid = f"normal-graph-{j}"
            g = self.generate_normal(gid)
            dataset.append(g)

        self.rng.shuffle(dataset)
        return dataset
