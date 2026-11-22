import importlib.util, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("wake_benchmark",Path(__file__).with_name("wake_benchmark.py"))
bench=importlib.util.module_from_spec(spec); spec.loader.exec_module(bench)

class BenchmarkTests(unittest.TestCase):
    def test_percentiles(self):
        self.assertEqual(bench.percentile([10,20,30,40],.5),20)
        self.assertEqual(bench.percentile([10,20,30,40],.95),40)
    def test_metrics(self):
        rows=[{"expected_wake":1,"detected":1,"latency_ms":250,"duration_s":0,"false_wakes":0},
              {"expected_wake":1,"detected":0,"latency_ms":None,"duration_s":0,"false_wakes":0},
              {"expected_wake":0,"detected":0,"latency_ms":None,"duration_s":1800,"false_wakes":2}]
        s=bench.summarize(rows)
        self.assertEqual(s["recall_percent"],50.0); self.assertEqual(s["false_wakes_per_negative_hour"],4.0)
        self.assertEqual(s["wake_latency_p50_ms"],250)
    def test_duplicate_ids_rejected(self):
        header="trial_id,split,expected_wake,phrase_or_negative_type,speaker_id,distance_m,speech_level,noise_condition,noise_level_db,device_model,android_version,screen_state,service_active,detected,wake_latency_ms,false_wake_count,trial_duration_s,apk_commit,model_version,gate_config,notes\n"
        rows="X,evaluation,1,Jarvis,S01,0.5,normal,quiet,,POCO F5,15,on,1,1,250,0,5,abc,m1,g1,\nX,evaluation,0,silence,S01,0.5,normal,quiet,,POCO F5,15,on,1,0,,0,60,abc,m1,g1,\n"
        with tempfile.TemporaryDirectory() as d:
            f=Path(d)/"trials.csv"; f.write_text(header+rows)
            with self.assertRaisesRegex(ValueError,"duplicate trial_id"): bench.load_trials(f)

if __name__=="__main__": unittest.main()
