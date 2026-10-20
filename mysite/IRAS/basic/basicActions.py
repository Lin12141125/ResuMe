class IRecruiterManageBiz:
    def findRecruiterByAccount(self, account):
        # 根据指定招聘者账号，查询该账号内容，返回Recruiter
        pass

    def findRecruiterById(self, id):
        # 根据指定招聘者id编号，查询该账号内容，返回Recruiter
        pass

    def findAllAccount(self):
        # 查询所有的招聘者账号，返回所有Recruiter
        pass

    def addRecruiter(self, account, password):
        # 新增招聘者账号
        pass

    def updateRecruiterPwdById(self, id, password):
        # 更新招聘者账号的对应密码
        pass

    def deleteRecruiterById(self, id):
        # 删除指定id招聘者账号
        pass

    def loginCheck(self, account, password):
        # 根据账号和密码进行登录验证
        pass

    def registerCheck(self, account):
        pass

class IAdminManageBiz:
    def addAdministrator(self, account, password):
        # 新增管理员账号
        pass

    def deleteAdministratorById(self, id):
        # 删除指定id管理员账号
        pass

    def loginCheck(self, account, password):
        # 根据账号和密码进行登录验证
        pass


class IResumeManageBiz:
    def findAllResumeByRecruiterid(self, id):
        # 查找指定id招聘者账号所有的简历，返回所有的Resume
        pass

    def findResumeById(self, resumeid):
        # 查询指定编号简历内容，返回Resume
        pass

    def findAllResume(self):
        # 查询所有简历内容，返回Resumes
        pass

    def addResume(self, resumename, resumefiletype, recruiterid=None):
        # 新增简历
        pass

    def deleteResumeById(self, resumeid):
        # 删除指定编号的简历
        pass

    def deleteAllResumeByRecruiterid(self, id):
        # 删除指定id招聘者账号所有的简历
        pass

    def saveResumeFile(self, resumefile, resume):
        # 将简历文件保存到服务器指定位置
        pass


class IExtractedResumeManageBiz:
    def findExtractedResumeById(self, reid):
        # 查询指定解析简历编号的解析简历内容，返回ExtractedResume
        pass

    def findExtractedResumeByOriginResumeid(self, resumeid):
        # 查询原编号简历对应的解析简历内容，返回ExtractedResume
        pass

    def addExtractedResume(self, ExtractedResumeDetails):
        # 新增解析简历实体
        pass

    def deleteExtractedResumeById(self, reid):
        # 删除指定编号的简历
        pass
