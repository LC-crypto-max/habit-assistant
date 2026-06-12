import importlib.util
import pathlib
import sys
import unittest


MODULE_PATH = pathlib.Path(__file__).with_name("agent_reach_adapter.py")
SPEC = importlib.util.spec_from_file_location("agent_reach_adapter", MODULE_PATH)
adapter = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = adapter
SPEC.loader.exec_module(adapter)


class AgentReachAdapterTest(unittest.TestCase):
    def test_enriches_bilibili_public_url_with_mock_structure(self):
        item = {
            "userId": "me",
            "platform": "browser",
            "source": "browser-history",
            "title": "Spring Boot Redis cache tutorial",
            "url": "https://www.bilibili.com/video/BV1demo",
            "occurredAt": "2026-06-06T22:00:00",
            "rawEvidence": {
                "browser": "edge",
                "domain": "bilibili.com",
                "visitCount": 3,
            },
        }

        enriched = adapter.enrich_public_url(item)

        self.assertEqual(enriched["platform"], "bilibili")
        self.assertEqual(enriched["source"], "agent-reach-mock")
        self.assertEqual(enriched["contentType"], "video")
        self.assertEqual(enriched["confidence"], "MEDIUM")
        self.assertEqual(enriched["dataLevel"], "PUBLIC_URL")
        self.assertEqual(enriched["rawEvidence"]["adapter"], "agent-reach")
        self.assertEqual(enriched["rawEvidence"]["adapterMode"], "mock")
        self.assertIn("Java后端", enriched["tags"])

    def test_non_public_url_is_returned_unchanged(self):
        item = {"platform": "desktop-app", "url": "", "title": "Visible app: chrome"}

        self.assertEqual(adapter.enrich_public_url(item), item)


if __name__ == "__main__":
    unittest.main()
