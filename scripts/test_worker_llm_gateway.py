import pathlib
import sys
import unittest


sys.path.insert(0, str(pathlib.Path(__file__).parent))

from policy_gate import PolicyDecision
from worker_llm_gateway import WorkerLLMGateway


class _RecordingAnalyzer:
    def __init__(self):
        self.items = []

    def submit(self, item):
        self.items.append(item)
        return None


class WorkerLLMGatewayTest(unittest.TestCase):
    def test_required_policy_decision_uses_analyzer_public_submit_api(self):
        item = {
            "platform": "xiaohongshu",
            "eventType": "VISIT",
            "url": "https://www.xiaohongshu.com/explore/note123",
        }
        decision = PolicyDecision(
            item=item,
            platform="xiaohongshu",
            event_type="VISIT",
            llm_required=True,
            reason="content_event_requires_llm",
            gateway_path="PolicyGate -> WorkerLLMGateway",
            final_ingestion_path="PolicyGate -> WorkerLLMGateway -> ingestion",
        )
        gateway = WorkerLLMGateway.__new__(WorkerLLMGateway)
        gateway.analyzer = _RecordingAnalyzer()

        futures = gateway.submit_policy_decisions([decision])

        self.assertEqual(futures, [])
        self.assertEqual(gateway.analyzer.items, [item])


if __name__ == "__main__":
    unittest.main()
