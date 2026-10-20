import json
import logging
from functools import wraps
from pathlib import Path

from django.conf import settings
from django.contrib.auth.hashers import check_password, identify_hasher, make_password
from django.core import signing
from django.db import transaction
from django.http import JsonResponse
from django.utils.crypto import constant_time_compare
from openai import OpenAIError
from pdfminer.high_level import extract_text

from IRAS.models import RiesAdministrator, RiesRecruiter, RiesResume, RiesResumeExtracted
from IRAS.utils.resumeExtration import ParserConfigurationError, calculate_work_years, parse_resume
from IRAS.utils.resumeVisualization import InformationStatistics

logger = logging.getLogger(__name__)
TOKEN_SALT = "iras-login"


class RequestError(Exception):
    def __init__(self, detail, status=400):
        self.detail = detail
        self.status = status


def api(method, role=None):
    def decorate(view):
        @wraps(view)
        def wrapped(request):
            if request.method != method:
                response = JsonResponse({"msg": "error", "code": 405}, status=405)
                response["Allow"] = method
                return response
            try:
                request.api_data = {}
                if method == "POST" and request.content_type == "application/json":
                    request.api_data = json.loads(request.body)
                    if not isinstance(request.api_data, dict):
                        raise RequestError("请求必须是 JSON 对象")
                if role:
                    authorization = request.headers.get("Authorization", "")
                    if not authorization.startswith("Bearer "):
                        raise RequestError("请先登录", 401)
                    try:
                        identity = signing.loads(authorization[7:], salt=TOKEN_SALT, max_age=86400)
                    except signing.BadSignature:
                        raise RequestError("登录已失效，请重新登录", 401)
                    request.identity = identity
                    if role == "admin" and identity["role"] != "admin":
                        raise RequestError("需要管理员权限", 403)
                    model = RiesAdministrator if identity["role"] == "admin" else RiesRecruiter
                    if not model.objects.filter(pk=identity["id"]).exists():
                        raise RequestError("账号不存在", 401)
                return view(request)
            except RequestError as error:
                return JsonResponse({"msg": "error", "code": error.status, "detail": error.detail}, status=error.status)
            except (ValueError, TypeError, KeyError):
                return JsonResponse({"msg": "error", "code": 400, "detail": "参数格式错误"}, status=400)
        return wrapped
    return decorate


def token_for(account, role):
    return signing.dumps({"id": account.pk, "role": role}, salt=TOKEN_SALT)


def credentials(request, account_field):
    account = request.api_data.get(account_field)
    password = request.api_data.get("password")
    if not isinstance(account, str) or not account.strip() or len(account) > 60:
        raise RequestError("账号不能为空且不能超过60个字符")
    if not isinstance(password, str) or not password or len(password) > 256:
        raise RequestError("密码不能为空且不能超过256个字符")
    return account.strip(), password


def authenticate(account, password):
    if account is None:
        return False
    try:
        identify_hasher(account.password)
    except ValueError:
        valid = constant_time_compare(account.password, password)
        if valid:
            account.password = make_password(password)
            account.save(update_fields=["password"])
        return valid
    return check_password(password, account.password)


def visible_resumes(request):
    resumes = RiesResume.objects.all()
    if request.identity["role"] != "admin":
        resumes = resumes.filter(recruiter_id=request.identity["id"])
    return resumes


def target_user(request):
    requested = int(request.GET.get("user_id", request.identity["id"]))
    if request.identity["role"] != "admin" and requested != request.identity["id"]:
        raise RequestError("不能访问其他用户的数据", 403)
    return requested


@api("GET")
def common(request):
    return JsonResponse({"msg": "success", "code": 200, "parser": settings.RESUME_PARSER})


@api("POST")
def user_register(request):
    account, password = credentials(request, "user_account")
    if RiesRecruiter.objects.filter(recruiter_account=account).exists():
        return JsonResponse({"msg": "failure", "code": 301})
    user = RiesRecruiter.objects.create(recruiter_account=account, password=make_password(password))
    return JsonResponse({"msg": "success", "code": 200, "user_id": user.pk, "token": token_for(user, "user")})


@api("POST")
def user_login(request):
    account, password = credentials(request, "user_account")
    user = RiesRecruiter.objects.filter(recruiter_account=account).first()
    if not authenticate(user, password):
        return JsonResponse({"msg": "failure", "code": 301 if user else 302})
    return JsonResponse({"msg": "success", "code": 200, "user_id": user.pk, "token": token_for(user, "user")})


@api("POST")
def admin_login(request):
    account, password = credentials(request, "admin_account")
    admin = RiesAdministrator.objects.filter(admin_account=account).first()
    if not authenticate(admin, password):
        return JsonResponse({"msg": "failure", "code": 301 if admin else 302})
    return JsonResponse({"msg": "success", "code": 200, "admin_id": admin.pk, "token": token_for(admin, "admin")})


@api("POST", "user")
def resume_upload2interpret(request):
    if request.identity["role"] != "user":
        raise RequestError("请使用招聘者账号上传", 403)
    owner = request.identity["id"]
    if int(request.POST.get("user_id", owner)) != owner:
        raise RequestError("不能为其他用户上传", 403)
    uploads = request.FILES.getlist("file")
    if not uploads or len(uploads) > 5:
        raise RequestError("请选择1至5份简历")
    prepared = []
    for upload in uploads:
        suffix = Path(upload.name).suffix.lower()
        if suffix not in (".pdf", ".txt", ".text"):
            raise RequestError("仅支持 PDF 和 UTF-8 文本简历")
        if not 0 < upload.size <= settings.MAX_RESUME_BYTES or len(upload.name) > 60:
            raise RequestError("文件为空、超过10MB或文件名超过60字符")
        try:
            text = extract_text(upload.file) if suffix == ".pdf" else upload.read().decode("utf-8-sig")
        except Exception:
            raise RequestError("无法读取简历，请上传有效 PDF 或 UTF-8 文本")
        if not text.strip():
            raise RequestError("未提取到文本；扫描版 PDF 需先进行 OCR")
        if len(text) > 50000:
            raise RequestError("简历文本超过50000字符，请缩短后重试")
        try:
            result = parse_resume(text)
        except ParserConfigurationError as error:
            raise RequestError(str(error), 503)
        except (OpenAIError, ValueError, TypeError, AttributeError):
            raise RequestError("AI 服务调用失败或返回无效结果，请检查配置后重试", 502)
        upload.seek(0)
        prepared.append((upload, suffix, result))
    created_paths = []
    records = []
    try:
        with transaction.atomic():
            for upload, suffix, result in prepared:
                resume = RiesResume.objects.create(resume_name=upload.name, recruiter_id=owner)
                resume.resume_url = f"{resume.pk}{suffix}"
                resume.save(update_fields=["resume_url"])
                extracted = RiesResumeExtracted.objects.create(
                    resume=resume, resume_name=upload.name, name=result["姓名"][:100],
                    age=result["年龄"], degree=result["最高学历"][:16], school=result["学校"][:16],
                    address=result["籍贯"][:160], years=calculate_work_years(result["工作经历"]),
                )
                original_path = Path(settings.MEDIA_ROOT) / resume.resume_url
                json_path = Path(settings.RESUME_JSON_ROOT) / f"{extracted.pk}.json"
                for path in (original_path, json_path):
                    path.parent.mkdir(parents=True, exist_ok=True)
                    created_paths.append(path)
                with original_path.open("wb") as output:
                    for chunk in upload.chunks():
                        output.write(chunk)
                with json_path.open("w", encoding="utf-8") as output:
                    json.dump(result, output, ensure_ascii=False, indent=2)
                records.append({"resumecode": resume.pk, "resumename": resume.resume_name})
    except Exception:
        for path in created_paths:
            path.unlink(missing_ok=True)
        logger.exception("Resume persistence failed")
        raise RequestError("保存失败，请检查数据库和文件目录权限", 500)
    return JsonResponse({"msg": "success", "code": 200, "parser": settings.RESUME_PARSER, "data": records})


@api("GET", "user")
def find_user_resumes(request):
    resumes = visible_resumes(request).filter(recruiter_id=target_user(request))
    extracted_ids = set(RiesResumeExtracted.objects.filter(resume__in=resumes).values_list("resume_id", flat=True))
    return JsonResponse({"msg": "success", "code": 200, "data": [
        {"resumecode": resume.pk, "resumename": resume.resume_name,
         "state": "已解析" if resume.pk in extracted_ids else "已上传"} for resume in resumes
    ]})


@api("GET", "user")
def single_resume_present(request):
    resume = visible_resumes(request).filter(pk=int(request.GET["resumecode"])).first()
    if resume is None:
        raise RequestError("简历不存在或无权访问", 404)
    extracted = RiesResumeExtracted.objects.filter(resume=resume).first()
    if extracted is None:
        raise RequestError("简历尚未解析", 404)
    path = Path(settings.RESUME_JSON_ROOT) / f"{extracted.pk}.json"
    try:
        with path.open(encoding="utf-8") as source:
            result = json.load(source)
    except (OSError, ValueError):
        raise RequestError("解析文件缺失或损坏，请重新上传", 404)
    return JsonResponse({"msg": "success", "code": 200, "data": result})


@api("GET", "user")
def resume_statistic(request):
    user_id = target_user(request)
    codes = request.GET.get("code_list")
    if codes is not None:
        codes = json.loads(codes)
        if not isinstance(codes, list):
            raise RequestError("code_list 必须是 JSON 数组")
        codes = [int(code) for code in codes]
        allowed = visible_resumes(request).filter(recruiter_id=user_id, pk__in=codes).count()
        if allowed != len(set(codes)):
            raise RequestError("包含不可访问的简历", 403)
    return JsonResponse(InformationStatistics().exec(user_id, codes))


def delete_resumes(resumes):
    paths = []
    for resume in resumes:
        paths.append(Path(settings.MEDIA_ROOT) / Path(resume.resume_url).name)
        paths.extend(Path(settings.RESUME_JSON_ROOT) / f"{record.pk}.json"
                     for record in RiesResumeExtracted.objects.filter(resume=resume))
    with transaction.atomic():
        resumes.delete()
    for path in paths:
        try:
            path.unlink(missing_ok=True)
        except OSError:
            logger.warning("Could not remove resume file: %s", path.name)


@api("POST", "user")
def resume_delete(request):
    codes = request.api_data.get("code_list")
    if not isinstance(codes, list) or not codes:
        raise RequestError("code_list 必须是非空数组")
    codes = {int(code) for code in codes}
    resumes = visible_resumes(request).filter(pk__in=codes)
    if resumes.count() != len(codes):
        raise RequestError("简历不存在或无权删除", 404)
    delete_resumes(resumes)
    return JsonResponse({"msg": "success", "code": 200})


@api("POST", "admin")
def user_delete(request):
    user_id = int(request.api_data["user_id"])
    user = RiesRecruiter.objects.filter(pk=user_id).first()
    if user is None:
        raise RequestError("用户不存在", 404)
    delete_resumes(RiesResume.objects.filter(recruiter=user))
    user.delete()
    return JsonResponse({"msg": "success", "code": 200})


@api("GET", "admin")
def find_all_users(request):
    return JsonResponse({"msg": "success", "code": 200, "data": [
        {"user_id": user.pk, "user_type": "招聘者", "user_account": user.recruiter_account}
        for user in RiesRecruiter.objects.all()
    ]})