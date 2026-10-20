import os

from .basicActions import *
from .DAOImp import *
from .entities import *
from django.contrib.auth.hashers import make_password, check_password
from django.conf import settings

base_dir = settings.MEDIA_ROOT
outputfile = settings.RESUME_JSON_ROOT


class RecruiterManageBiz(IRecruiterManageBiz):
    def findRecruiterByAccount(self, account):
        # 根据指定招聘者账号，查询该账号内容，返回Recruiter
        item = RecruiterDao.findRecruiterByAccount(account)
        recruiter = Recruiter(item.recruiter_id, item.recruiter_account, item.password)
        return recruiter

    def findRecruiterById(self, id):
        # 根据指定招聘者id编号，查询该账号内容，返回Recruiter
        item = RecruiterDao.findRecruiterById(id)
        recruiter = Recruiter(item.recruiter_id, item.recruiter_account, item.password)
        return recruiter

    def findAllAccount(self):
        # 查询所有的招聘者账号，返回所有Recruiter
        recruiters = []
        queryset = RecruiterDao.findAllAccount()
        if not queryset.exists():
            return None
        for item in queryset:
            recruiter = Recruiter(
                item.recruiter_id, item.recruiter_account, item.password
            )
            recruiters.append(recruiter)
        return recruiters

    def addRecruiter(self, account, password):
        # 新增招聘者账号
        item = RecruiterDao.addRecruiter(account, password)
        recruiter = Recruiter(item.recruiter_id, item.recruiter_account, item.password)
        return recruiter

    def updateRecruiterPwdById(self, id, password):
        # 更新招聘者账号的对应密码
        item = RecruiterDao.updateRecruiterPwdById(id, password)
        recruiter = Recruiter(item.recruiter_id, item.recruiter_account, item.password)
        return recruiter

    def deleteRecruiterById(self, id):
        # 删除指定id招聘者账号
        item = RecruiterDao.findRecruiterById(id)
        if item is not None:
            RecruiterDao.deleteRecruiterById(id)
            return True
        else:
            return False

    def loginCheck(self, account, password):
        # 根据账号和密码进行登录验证
        item = RecruiterDao.checkRecruiterByAccount(account)
        if item is not None:
            # 如果账号存在
            pwd = make_password(item.password)
            if check_password(password, pwd):
                return item.recruiter_id
            else:
                return "WrongPwd"
        else:
            return "NotExist"

    def registerCheck(self, account):
        item = RecruiterDao.checkRecruiterByAccount(account)
        if item is not None:
            return True
        else:
            return False


class AdminManageBiz(IAdminManageBiz):
    def addAdministrator(self, account, password):
        # 新增管理员账号
        item = AdministratorDao.addAdministrator(account, password)
        admin = Administrator(item.admin_id, item.admin_account, item.password)
        return admin

    def deleteAdministratorById(self, id):
        # 删除指定id管理员账号
        AdministratorDao.deleteAdministratorById(id)

    def loginCheck(self, account, password):
        # 根据账号和密码进行登录验证
        item = AdministratorDao.checkAdministratorByAccount(account)
        if item is not None:
            # 如果账号存在
            pwd = make_password(item.password)
            if check_password(password, pwd):
                return item.admin_id
            else:
                return "WrongPwd"
        else:
            return "NotExist"


class ResumeManageBiz(IResumeManageBiz):
    def findAllResumeByRecruiterid(self, id):
        # 查找指定id招聘者账号所有的简历，返回所有的Resume
        resumes = []
        queryset = ResumeDao.findAllResumeByRecruiterid(id)
        if not queryset.exists():
            return None
        for item in queryset:
            resume = Resume(
                item.resume_id, item.resume_name, item.resume_url, item.recruiter_id
            )
            resumes.append(resume)
        return resumes

    def findResumeById(self, resumeid):
        # 查询指定编号简历内容，返回Resume
        item = ResumeDao.findResumeById(resumeid)
        resume = Resume(
            item.resume_id, item.resume_name, item.resume_url, item.recruiter_id
        )
        return resume

    def findAllResume(self):
        # 查询所有简历内容，返回Resumes
        resumes = []
        queryset = ResumeDao.findAllResume()
        for item in queryset:
            resume = Resume(
                item.resume_id, item.resume_name, item.resume_url, item.recruiter_id
            )
            resumes.append(resume)
        return resumes

    def addResume(self, resumename, resumefiletype, recruiterid=None):
        # 新增简历
        if resumefiletype == "txt":
            resumeurl = "{}.txt"
        else:
            resumeurl = "{}.pdf"
        item = ResumeDao.addResume(resumename, resumeurl, recruiterid)
        resume = Resume(
            item.resume_id, item.resume_name, item.resume_url, item.recruiter_id
        )
        return resume

    def deleteResumeById(self, resumeid):
        # 删除指定编号的简历, 会先删除文件再进行简历删除
        item = ResumeDao.findResumeById(resumeid)
        if item is None:  # 如果不存在返回失败删除结果
            return False
        # 先删除对应的解析文件
        extractedresume = ExtractedResumeDao.findExtractedResumeByOriginResumeid(resumeid)
        if extractedresume is not None:
            extractedresumeurl = "{}.json".format(extractedresume.resume_extracted_id)
            path = os.path.join(outputfile, extractedresumeurl)
            if os.path.exists(path):
                os.remove(path)
            print(path)
        # 再删除对应的原pdf文件
        url = item.resume_url
        path = os.path.join(base_dir, url)
        if os.path.exists(path):
            os.remove(path)
        ResumeDao.deleteResumeById(resumeid)
        return True

    def deleteAllResumeByRecruiterid(self, id):
        # 删除指定id招聘者账号所有的简历
        queryset = ResumeDao.findAllResumeByRecruiterid(id)
        for item in queryset:
            path = os.path.join(base_dir, item.resume_url)
            if os.path.exists(path):
                os.remove(path)
        ResumeDao.deleteAllResumeByRecruiterId(id)

    def saveResumeFile(self, resumefile, resume):
        # 将简历文件保存到服务器指定位置
        fileurl = resume.getUrl()
        savepath = os.path.join(base_dir, fileurl)
        print(savepath)
        with open(savepath, "wb") as f:
            for part in resumefile.chunks():
                f.write(part)
        f.close()


class ExtractedResumeManageBiz(IExtractedResumeManageBiz):
    def findExtractedResumeById(self, reid):
        # 查询指定编号的解析简历内容，返回ExtractedResume
        item = ExtractedResumeDao.findExtractedResumeById(reid)
        extractedresume = ExtractedResume(
            item.resume_extracted_id,
            item.resume_id,
            item.resume_name,
            item.name,
            item.age,
            item.degree,
            item.school,
            item.address,
            item.years,
        )
        return extractedresume

    def findExtractedResumeByOriginResumeid(self, resumeid):
        # 查询原编号简历对应的解析简历内容，返回extractedresume
        item = ExtractedResumeDao.findExtractedResumeByOriginResumeid(resumeid)
        if item is None:
            return None
        extractedresume = ExtractedResume(
            item.resume_extracted_id,
            item.resume_id,
            item.resume_name,
            item.name,
            item.age,
            item.degree,
            item.school,
            item.address,
            item.years,
        )
        return extractedresume

    def addExtractedResume(self, ExtractedResumeDetails):
        """
        新增解析简历
        直接返回 DAO 层已经保存到数据库的对象
        """
        try:
            print(f"🔍 正在创建解析简历记录: {ExtractedResumeDetails}")
            
            # 调用 DAO 层方法，该方法已经将数据保存到数据库
            extracted_resume = ExtractedResumeDao.addExtractedResumeById(
                ExtractedResumeDetails["resumeid"],
                ExtractedResumeDetails["resumename"],
                ExtractedResumeDetails["name"],
                ExtractedResumeDetails["age"],
                ExtractedResumeDetails["degree"],
                ExtractedResumeDetails["school"],
                ExtractedResumeDetails["address"],
                ExtractedResumeDetails["years"],
            )
            
            print(f"✅ 解析简历记录创建成功，ID: {extracted_resume.resume_extracted_id}")
            return extracted_resume
            
        except Exception as e:
            print(f"❌ 创建解析简历记录失败: {str(e)}")
            raise e

    def deleteExtractedResumeById(self, reid):
        # 删除指定编号的简历
        ExtractedResumeDao.deleteResumeById(reid)
