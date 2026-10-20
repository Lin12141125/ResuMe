from IRAS.basic.basicActionsImp import *

class RecruiterAdmin:
    def __init__(self):
        self.remb = RecruiterManageBiz()
        self.rmb = ResumeManageBiz()
        self.irmb = ExtractedResumeManageBiz()

    def findMyResume(self, id):
        # 查询招聘者对应的所有简历
        data = {"msg": "success", "code": 200}
        result_set = []
        resumes = self.rmb.findAllResumeByRecruiterid(id)
        if resumes is None:
            data["msg"] = "success"
            data["code"] = 200
            data["data"] = []
            return data
        
        for resume in resumes:
            # 检查是否存在对应的解析记录
            extracted_resume = self.irmb.findExtractedResumeByOriginResumeid(resume.getResumeId())
            
            # 如果存在解析记录，状态为"已解析"，否则为"已上传"
            state = "已解析" if extracted_resume is not None else "已上传"
            
            result = {
                "resumecode": resume.getResumeId(),
                "resumename": resume.getResumeName(),
                "state": state,
            }
            result_set.append(result)
        
        data["data"] = result_set
        return data

    def deleteResume(self, resumecodes):
        # 删除对应简历
        data = {"msg": "success", "code": 200}
        for code in resumecodes:
            if not self.rmb.deleteResumeById(code):
                data["msg"] = "failure"
                data["code"] = 301
                break
        return data

    def register(self, account, password):
        # 招聘者注册
        data = {"msg": "success", "code": 200}
        if self.remb.registerCheck(account):
            #  如果账号存在就返回301，账号已存在
            data["msg"] = "failure"
            data["code"] = 301
        else:
            data["user_id"] = self.remb.addRecruiter(account, password).getId()
        return data

    def login(self, account, password):
        # 招聘者登录
        data = {"msg": "success", "code": 200}
        mark = self.remb.loginCheck(account, password)
        if mark == "WrongPwd":
            data["msg"] = "failure"
            data["code"] = 301
        elif mark == "NotExist":
            data["msg"] = "failure"
            data["code"] = 302
        else:
            data["user_id"] = mark
        return data
