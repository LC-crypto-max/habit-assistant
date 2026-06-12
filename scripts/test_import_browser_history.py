import importlib.util
import json
import pathlib
import sqlite3
import sys
import unittest
from datetime import datetime, timezone
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("import_browser_history.py")
SPEC = importlib.util.spec_from_file_location("import_browser_history", MODULE_PATH)
importer = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = importer
SPEC.loader.exec_module(importer)


def chrome_time(year: int = 2026, month: int = 6, day: int = 6, hour: int = 22) -> int:
    dt = datetime(year, month, day, hour, 0, 0, tzinfo=timezone.utc)
    return int((dt - importer.CHROME_EPOCH).total_seconds() * 1_000_000)


class BrowserHistoryImportTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.create_history(self.conn)

    def tearDown(self):
        self.conn.close()

    def create_history(self, conn: sqlite3.Connection):
        conn.execute("""
                CREATE TABLE urls (
                    id INTEGER PRIMARY KEY,
                    url TEXT NOT NULL,
                    title TEXT,
                    visit_count INTEGER,
                    last_visit_time INTEGER
                )
            """)
        conn.execute("""
                CREATE TABLE visits (
                    id INTEGER PRIMARY KEY,
                    url INTEGER NOT NULL,
                    visit_time INTEGER
                )
            """)
        rows = [
            (1, "https://www.xiaohongshu.com/explore/demo", "小红书 AI 笔记", 3, chrome_time()),
            (2, "https://www.youtube.com/watch?v=demo", "YouTube Java Tutorial", 5, chrome_time(hour=21)),
            (3, "https://www.bilibili.com/video/BV1demo", "B站 Spring Boot 视频", 2, chrome_time(hour=20)),
            (4, "https://example.com/plain", "Plain Example", 1, chrome_time(hour=19)),
        ]
        conn.executemany("INSERT INTO urls VALUES (?, ?, ?, ?, ?)", rows)
        conn.executemany(
            "INSERT INTO visits(url, visit_time) VALUES (?, ?)",
            [(row[0], row[4]) for row in rows],
        )
        conn.commit()

    def test_reads_simulated_history_sqlite(self):
        rows = importer.query_history(self.conn, "edge", "all", 10)

        self.assertEqual(len(rows), 3)
        self.assertEqual(rows[0].title, "小红书 AI 笔记")

    def test_filters_xiaohongshu_domain(self):
        rows = importer.query_history(self.conn, "edge", "xiaohongshu", 10)
        events = importer.rows_to_events(rows, "me")

        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]["platform"], "xiaohongshu")
        self.assertEqual(events[0]["url"], "https://www.xiaohongshu.com/explore/demo")
        self.assertEqual(events[0]["matchedKeyword"], "xiaohongshu.com")

    def test_filters_youtube_domain(self):
        rows = importer.query_history(self.conn, "chrome", "youtube", 10)
        events = importer.rows_to_events(rows, "me")

        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]["platform"], "youtube")
        self.assertIn("youtube.com", events[0]["rawEvidence"]["domain"])

    def test_does_not_read_cookie_database(self):
        rows = importer.query_history(self.conn, "edge", "xiaohongshu", 10)

        self.assertEqual(len(rows), 1)
        self.assertNotIn("cookie", json.dumps([row.__dict__ for row in rows], ensure_ascii=False).lower())

    def test_chinese_title_is_not_mojibake(self):
        rows = importer.query_history(self.conn, "edge", "xiaohongshu", 10)
        payload = json.dumps({"events": importer.rows_to_events(rows, "me")}, ensure_ascii=False)

        self.assertIn("小红书 AI 笔记", payload)

    def test_upload_payload_keeps_url(self):
        args = importer.parse_args([
            "--user-id", "me",
            "--browser", "edge",
            "--platform", "xiaohongshu",
            "--base-url", "http://localhost:8080",
        ])
        fake_rows = importer.query_history(self.conn, "edge", "xiaohongshu", 10)
        with patch.object(importer, "locate_history_files", return_value=[pathlib.Path("History")]), \
                patch.object(importer, "read_history", return_value=fake_rows), \
                patch.object(importer, "post_json", return_value={"imported": 1}) as post_json:
            result = importer.import_history(args)

        self.assertEqual(result["imported"], 1)
        payload = post_json.call_args.args[1]
        self.assertEqual(payload["events"][0]["url"], "https://www.xiaohongshu.com/explore/demo")
        self.assertEqual(payload["events"][0]["confidence"], "MEDIUM")


if __name__ == "__main__":
    unittest.main()
