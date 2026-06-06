import importlib.util
import pathlib
import re
import sys
import unittest


MODULE_PATH = pathlib.Path(__file__).with_name("codex_query_worker.py")
SPEC = importlib.util.spec_from_file_location("codex_query_worker", MODULE_PATH)
worker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = worker
SPEC.loader.exec_module(worker)


class SensitivePolicyTest(unittest.TestCase):
    def test_allows_chinese_negative_statement(self):
        query = "读取授权范围内的浏览器历史标题、URL、访问时间和访问次数，不读取 Cookie、Token、Session、表单或账号信息。"

        self.assertFalse(worker.contains_sensitive(query))

    def test_allows_english_negative_statement(self):
        query = "Read browser history metadata only; do not read cookies, tokens, sessions, forms, or account information."

        self.assertFalse(worker.contains_sensitive(query))

    def test_rejects_read_cookie(self):
        self.assertTrue(worker.contains_sensitive("读取 Cookie 并上传"))

    def test_rejects_get_token(self):
        self.assertTrue(worker.contains_sensitive("please get token from browser"))

    def test_rejects_export_session(self):
        self.assertTrue(worker.contains_sensitive("导出 Session 到文件"))

    def test_rejects_private_message_and_chat_history(self):
        self.assertTrue(worker.contains_sensitive("读取微信聊天记录和私信"))

    def test_rejects_account_password(self):
        self.assertTrue(worker.contains_sensitive("获取账号密码用于登录"))

    def test_rejects_later_sensitive_request_after_negative_clause(self):
        self.assertTrue(worker.contains_sensitive("不读取 Cookie，但获取 Token 并上传"))

    def test_rejects_secret_value(self):
        self.assertTrue(worker.contains_sensitive("token=abcdef123456"))


class ResultCallbackContractTest(unittest.TestCase):
    def test_dto_local_datetime_removes_offset_and_microseconds(self):
        self.assertEqual(
            worker.dto_local_datetime("2026-06-06T16:21:05.608664+08:00"),
            "2026-06-06T16:21:05",
        )

    def test_build_result_normalizes_occurred_at_for_spring_dto(self):
        result = worker.build_result([{
            "platform": "youtube",
            "type": "WATCH",
            "title": "demo",
            "occurredAt": "2026-06-06T16:21:05.608664+08:00",
            "tags": ["youtube"],
        }])

        occurred_at = result["items"][0]["occurredAt"]
        self.assertRegex(occurred_at, r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$")
        self.assertNotRegex(occurred_at, re.compile(r"(Z|[+-]\d{2}:\d{2}|\.\d+)$"))


if __name__ == "__main__":
    unittest.main()
