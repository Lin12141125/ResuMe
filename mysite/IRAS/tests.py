import json
import tempfile
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace

from django.contrib.auth.hashers import check_password
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from django.test import override_settings
from IRAS.models import RiesAdministrator, RiesRecruiter, RiesResume, RiesResumeExtracted
from IRAS.utils.resumeExtration import calculate_work_years, normalize_result, parse_resume


class ResumeWorkflowTests(TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.settings_override = override_settings(
            RESUME_PARSER="local", MEDIA_ROOT=Path(self.directory.name) / "files",
            RESUME_JSON_ROOT=Path(self.directory.name) / "json",
        )
        self.settings_override.enable()
        self.addCleanup(self.settings_override.disable)
        response = self.client.post("/IRAS/UserRegister/", {
            "user_account": "test-user", "password": "test-password", "user_type": 1,
        }, content_type="application/json").json()
        self.user_id = response["user_id"]
        self.client.defaults["HTTP_AUTHORIZATION"] = "Bearer " + response["token"]

    def upload(self, content="姓名: 测试者\n年龄: 26\n学历: 本科\n学校: 香港大学\n籍贯: 广东"):
        return self.client.post("/IRAS/ResumeUpload2Interpret/", {
            "user_id": self.user_id, "file": SimpleUploadedFile("resume.txt", content.encode("utf-8")),
        })

    def test_full_resume_lifecycle(self):
        self.assertTrue(check_password("test-password", RiesRecruiter.objects.get(pk=self.user_id).password))
        response = self.upload()
        self.assertEqual(response.status_code, 200, response.content)
        resume_id = response.json()["data"][0]["resumecode"]
        resume = RiesResume.objects.get(pk=resume_id)
        self.assertTrue((Path(self.directory.name) / "files" / resume.resume_url).exists())
        listing = self.client.get("/IRAS/FindUserResumes/", {"user_id": self.user_id}).json()
        self.assertEqual(listing["data"][0]["state"], "已解析")
        detail = self.client.get("/IRAS/SingleResumePresent/", {"resumecode": resume_id}).json()
        self.assertEqual(detail["data"]["姓名"], "测试者")
        statistics = self.client.get("/IRAS/ResumeStatistic/", {"user_id": self.user_id}).json()
        self.assertEqual(statistics["data"]["age_part"]["25_30"], 1)
        response = self.client.post("/IRAS/ResumeDelete/", {"code_list": [resume_id]}, content_type="application/json")
        self.assertEqual(response.status_code, 200)
        self.assertFalse(RiesResume.objects.exists())
        self.assertFalse(RiesResumeExtracted.objects.exists())
        self.assertEqual(list(Path(self.directory.name).rglob("*.*")), [])

    def test_empty_and_unparsed_statistics(self):
        response = self.client.get("/IRAS/ResumeStatistic/", {"user_id": self.user_id})
        self.assertEqual(response.status_code, 200)
        RiesResume.objects.create(resume_name="unparsed", recruiter_id=self.user_id)
        self.assertEqual(self.client.get("/IRAS/ResumeStatistic/", {"user_id": self.user_id}).status_code, 200)

    def test_reject_invalid_upload_and_missing_credentials(self):
        self.assertEqual(self.client.post("/IRAS/ResumeUpload2Interpret/").status_code, 400)
        self.assertEqual(self.upload("").status_code, 400)
        with override_settings(RESUME_PARSER="llm", OPENAI_API_KEY=""):
            self.assertEqual(self.upload().status_code, 503)
        self.assertFalse(RiesResume.objects.exists())

    def test_llm_and_storage_failures_leave_no_records(self):
        with patch("IRAS.views.parse_resume", side_effect=ValueError("bad JSON")):
            self.assertEqual(self.upload().status_code, 502)
        with patch("IRAS.views.json.dump", side_effect=OSError("disk unavailable")):
            self.assertEqual(self.upload().status_code, 500)
        self.assertFalse(RiesResume.objects.exists())
        self.assertFalse(RiesResumeExtracted.objects.exists())
        self.assertEqual(list(Path(self.directory.name).rglob("*.*")), [])

    def test_cross_user_and_anonymous_access_denied(self):
        response = self.upload().json()
        resume_id = response["data"][0]["resumecode"]
        other = self.client.post("/IRAS/UserRegister/", {
            "user_account": "other", "password": "other-password",
        }, content_type="application/json").json()
        self.client.defaults["HTTP_AUTHORIZATION"] = "Bearer " + other["token"]
        self.assertEqual(self.client.get("/IRAS/SingleResumePresent/", {"resumecode": resume_id}).status_code, 404)
        self.assertEqual(self.client.get("/IRAS/FindUserResumes/", {"user_id": self.user_id}).status_code, 403)
        self.assertEqual(self.client.post("/IRAS/ResumeDelete/", {"code_list": [resume_id]}, content_type="application/json").status_code, 404)
        self.assertEqual(self.client.get("/IRAS/FindAllUsers/").status_code, 403)
        self.client.defaults.clear()
        self.assertEqual(self.client.get("/IRAS/FindUserResumes/").status_code, 401)

    def test_login_and_input_errors(self):
        wrong = self.client.post("/IRAS/UserLogin/", {"user_account": "test-user", "password": "wrong"}, content_type="application/json")
        self.assertEqual(wrong.json()["code"], 301)
        valid = self.client.post("/IRAS/UserLogin/", {"user_account": "test-user", "password": "test-password"}, content_type="application/json")
        self.assertIn("token", valid.json())
        self.assertEqual(self.client.get("/IRAS/UserLogin/").status_code, 405)
        self.assertEqual(self.client.post("/IRAS/UserLogin/", "{", content_type="application/json").status_code, 400)

    def test_admin_listing_omits_password_and_deletes_user(self):
        RiesAdministrator.objects.create(admin_account="admin-test", password="legacy-password")
        login = self.client.post("/IRAS/AdminLogin/", {"admin_account": "admin-test", "password": "legacy-password"}, content_type="application/json").json()
        self.client.defaults["HTTP_AUTHORIZATION"] = "Bearer " + login["token"]
        listing = self.client.get("/IRAS/FindAllUsers/").json()
        self.assertNotIn("user_password", listing["data"][0])
        self.assertEqual(self.client.post("/IRAS/UserDelete/", {"user_id": self.user_id}, content_type="application/json").status_code, 200)
        self.assertFalse(RiesRecruiter.objects.filter(pk=self.user_id).exists())

    def test_work_years_and_normalization(self):
        self.assertEqual(calculate_work_years([{"时间": "2020-2023"}, {"时间": "2022-2024"}]), 4)
        result = normalize_result({"年龄": None, "技能专长": "Python", "工作经历": [None]})
        self.assertEqual(result["年龄"], 0)
        self.assertEqual(result["技能专长"], [])
        self.assertEqual(result["工作经历"], [])

    def test_pdf_upload_and_invalid_pdf(self):
        objects = [
            b"<< /Type /Catalog /Pages 2 0 R >>",
            b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        ]
        stream = b"BT /F1 12 Tf 72 720 Td (synthetic@example.com) Tj ET"
        objects.append(b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream")
        document = b"%PDF-1.4\n"
        offsets = []
        for index, value in enumerate(objects, start=1):
            offsets.append(len(document))
            document += f"{index} 0 obj\n".encode() + value + b"\nendobj\n"
        xref = len(document)
        document += b"xref\n0 6\n0000000000 65535 f \n"
        document += b"".join(f"{offset:010d} 00000 n \n".encode() for offset in offsets)
        document += f"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF".encode()
        response = self.client.post("/IRAS/ResumeUpload2Interpret/", {
            "file": SimpleUploadedFile("synthetic.pdf", document, content_type="application/pdf"),
        })
        self.assertEqual(response.status_code, 200, response.content)
        resume_id = response.json()["data"][0]["resumecode"]
        detail = self.client.get("/IRAS/SingleResumePresent/", {"resumecode": resume_id}).json()
        self.assertEqual(detail["data"]["邮箱"], "synthetic@example.com")
        invalid = self.client.post("/IRAS/ResumeUpload2Interpret/", {
            "file": SimpleUploadedFile("invalid.pdf", b"not a pdf"),
        })
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(RiesResume.objects.count(), 1)

    def test_llm_sdk_contract(self):
        response = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
            content='```json\n{"姓名": "模型测试", "年龄": "26岁", "技能专长": ["Python"]}\n```'
        ))])
        with override_settings(RESUME_PARSER="llm", OPENAI_API_KEY="synthetic-test-key"):
            with patch("IRAS.utils.resumeExtration.OpenAI") as constructor:
                client = constructor.return_value.__enter__.return_value
                client.chat.completions.create.return_value = response
                result = parse_resume("合成测试文本")
                self.assertEqual(result["姓名"], "模型测试")
                self.assertEqual(result["年龄"], 26)
                self.assertEqual(client.chat.completions.create.call_args.kwargs["response_format"], {"type": "json_object"})

    def test_selected_statistics_reject_other_owner(self):
        resume_id = self.upload().json()["data"][0]["resumecode"]
        response = self.client.get("/IRAS/ResumeStatistic/", {"code_list": json.dumps([resume_id])})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["data"]["age_part"]["25_30"], 1)
        self.assertEqual(self.client.get("/IRAS/ResumeStatistic/", {"code_list": "[999999]"}).status_code, 403)

    def test_batch_failure_and_unsupported_file_are_atomic(self):
        response = self.client.post("/IRAS/ResumeUpload2Interpret/", {"file": [
            SimpleUploadedFile("valid.txt", b"synthetic"),
            SimpleUploadedFile("invalid.exe", b"synthetic"),
        ]})
        self.assertEqual(response.status_code, 400)
        self.assertFalse(RiesResume.objects.exists())


