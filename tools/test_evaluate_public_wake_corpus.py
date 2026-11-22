import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "evaluate_public_wake_corpus",
    Path(__file__).with_name("evaluate_public_wake_corpus.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class PublicCorpusGateTests(unittest.TestCase):
    def test_gate_fast_path(self):
        gate = mod.ConfidenceGate()
        self.assertFalse(gate.add(.31))
        self.assertTrue(gate.add(.32))

    def test_gate_rejects_single_spike(self):
        gate = mod.ConfidenceGate()
        self.assertFalse(gate.add(.8))
        self.assertFalse(gate.add(.05))
        self.assertFalse(gate.add(.05))
        self.assertFalse(gate.add(.05))

    def test_gate_soft_consensus(self):
        gate = mod.ConfidenceGate()
        result = False
        for value in [.25, .25, .25, .25]:
            result = gate.add(value)
        self.assertTrue(result)


if __name__ == "__main__":
    unittest.main()