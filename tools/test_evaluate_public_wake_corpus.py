import importlib.util
from pathlib import Path
spec=importlib.util.spec_from_file_location("evaluate_public_wake_corpus",Path(__file__).with_name("evaluate_public_wake_corpus.py"))
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)

def test_gate_fast_path():
    gate=mod.ConfidenceGate()
    assert gate.add(.31) is False
    assert gate.add(.32) is True

def test_gate_rejects_single_spike():
    gate=mod.ConfidenceGate()
    assert gate.add(.8) is False
    assert gate.add(.05) is False
    assert gate.add(.05) is False
    assert gate.add(.05) is False

def test_gate_soft_consensus():
    gate=mod.ConfidenceGate()
    values=[.25,.25,.25,.25]
    result=False
    for value in values: result=gate.add(value)
    assert result is True
