from IRAS.basic.basicActionsImp import *


class BackStageManage:
    def __init__(self):
        self.remb = RecruiterManageBiz()
        self.rmb = ResumeManageBiz()
        self.irmb = ExtractedResumeManageBiz()
        self.amb = AdminManageBiz()

    def findAllRecruiter(self):
        data = {
            "msg": "success",
            "code": 200
        }
        result_set = []
        recruiters = self.remb.findAllAccount()
        if recruiters is None:
            data['msg'] = "failure"
            data['code'] = 301
            return data
        for recruiter in recruiters:
            result = {
                "user_id": recruiter.getId(),
                "user_type": '招聘者',
                "user_account": recruiter.getAccount(),
                "user_password": recruiter.getPwd()
            }
            result_set.append(result)
        data["data"] = result_set
        return data

    def addRecruiter(self, account, password):
        # 招聘者注册
        data = {
            "msg": "success",
            "code": 200
        }
        if self.remb.registerCheck(account):
            #  如果账号存在就返回301，账号已存在
            data['msg'] = 'failure'
            data['code'] = 301
        else:
            self.remb.addRecruiter(account, password)
        return data

    def deleteRecruiter(self, id):
        data = {
            "msg": "success",
            "code": 200
        }
        self.rmb.deleteAllResumeByRecruiterid(id)
        if self.remb.deleteRecruiterById(id):
            pass
        else:
            data['msg'] = 'failure'
            data['code'] = 301
        return data

    def deleteResume(self, resumecodes):
        # 删除对应简历
        data = {
            "msg": "success",
            "code": 200
        }
        for code in resumecodes:
            if not self.rmb.deleteResumeById(code):
                data['msg'] = 'failure'
                data['code'] = 301
                break
        return data

    def login(self, account, password):
        # 管理员登录
        data = {
            "msg": "success",
            "code": 200
        }
        mark = self.amb.loginCheck(account, password)
        if mark == "WrongPwd":
            data['msg'] = 'failure'
            data['code'] = 301
        elif mark == "NotExist":
            data['msg'] = 'failure'
            data['code'] = 302
        else:
            data['admin_id'] = mark
        return data
