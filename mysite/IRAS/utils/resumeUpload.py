from IRAS.basic.basicActionsImp import *


class TextEntryUpload:
    # 文本上传类
    def __init__(self, user_id, user_type):
        self.rmb = ResumeManageBiz()  # 实现IResumeManageBiz接口类的对象
        self.resumeName = None  # 简历名称
        self.resumeText = None  # 简历文本内容
        self.resumeFile = None  # 转换为txt的文本文件对象
        self.user_id = user_id  # 简历所属的招聘者id（如果上传者是求职者则为空）
        self.user_type = user_type

    def exec(self, file):
        self.receive(file)
        resume = self.save2DB()
        self.save2file(resume)
        return resume

    def receive(self, file):  # 接收简历文本
        self.resumeFile = file
        self.resumeName = file.name
        self.resumeText = file.read().decode('UTF-8')

    def save2file(self, resume):  # 存储为文件
        self.rmb.saveResumeFile(self.resumeFile, resume)

    def save2DB(self):  # 存储记录至数据库
        if self.user_type:
            return self.rmb.addResume(self.resumeName, "txt", self.user_id)
        else:
            # 对于求职者，要判断在这之前是否已经上传了简历，有的话要把文件和记录都删掉
            old_code = self.smb.findSeekerById(self.user_id).getResumecode()
            if old_code is not None:
                self.rmb.deleteResumeByCode(old_code)
            resume = self.rmb.addResume(self.resumeName, "txt")
            self.smb.updateSeekerResume(self.user_id, resume.getResumeCode())
            return resume


class PDFEntryUpload:
    # PDF文件上传类
    def __init__(self, user_id, user_type):
        self.rmb = ResumeManageBiz()  # 实现IResumeManageBiz接口类的对象
        self.resumeName = None  # 简历名称
        self.resumeFile = None  # 转换为txt的文本文件对象
        self.user_id = user_id  # 简历所属的招聘者id（如果上传者是求职者则为空）
        self.user_type = user_type

    def exec(self, file):
        self.receive(file)
        resume = self.save2DB()
        self.save2file(resume)
        return resume

    def receive(self, file):  # 接收简历文本
        self.resumeFile = file
        self.resumeName = file.name

    def save2file(self, resume):  # 存储文件
        self.rmb.saveResumeFile(self.resumeFile, resume)

    def save2DB(self):  # 存储记录至数据库
        if self.user_type:
            return self.rmb.addResume(self.resumeName, "pdf", self.user_id)
        else:
            # 对于求职者，要判断在这之前是否已经上传了简历，有的话要把文件和记录都删掉
            old_code = self.smb.findSeekerById(self.user_id).getResumecode()
            if old_code is not None:
                self.rmb.deleteResumeByCode(old_code)
            resume = self.rmb.addResume(self.resumeName, "pdf")
            self.smb.updateSeekerResume(self.user_id, resume.getResumeCode())
            return resume
