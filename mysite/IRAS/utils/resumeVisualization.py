import json
import os

from IRAS.basic.basicActionsImp import *

school_data = {
    "985学校": [
        "北京大学",
        "清华大学",
        "上海交通大学",
        "浙江大学",
        "武汉大学",
        "南京大学",
        "复旦大学",
        "中国科学技术大学",
        "华中科技大学",
        "中国人民大学",
        "天津大学",
        "北京师范大学",
        "西安交通大学",
        "吉林大学",
        "山东大学",
        "国防科技大学",
        "中山大学",
        "南开大学",
        "哈尔滨工业大学",
        "北京航空航天大学",
        "四川大学",
        "同济大学",
        "厦门大学",
        "北京理工大学",
        "东南大学",
        "华南理工大学",
        "大连理工大学",
        "华东师范大学",
        "中南大学",
        "西北工业大学",
        "中国农业大学",
        "东北大学",
        "湖南大学",
        "电子科技大学",
        "兰州大学",
        "重庆大学",
        "中国海洋大学",
        "西北农林科技大学",
        "中央民族大学"
    ],
    "211学校": [
        "北京科技大学",
        "武汉理工大学",
        "南京理工大学",
        "北京协和医学院",
        "西南大学",
        "华中师范大学",
        "暨南大学",
        "河海大学",
        "南京师范大学",
        "北京交通大学",
        "南京农业大学",
        "华东理工大学",
        "苏州大学",
        "华中农业大学",
        "中南财经政法大学",
        "南京航空航天大学",
        "西安电子科技大学",
        "合肥工业大学",
        "西南交通大学",
        "西北大学",
        "湖南师范大学",
        "郑州大学",
        "东北师范大学",
        "北京邮电大学",
        "华南师范大学",
        "哈尔滨工程大学",
        "上海财经大学",
        "云南大学",
        "上海大学",
        "西南财经大学",
        "东华大学",
        "中国政法大学",
        "海军军医大学",
        "北京化工大学",
        "中国石油大学（北京）",
        "中国地质大学（武汉）",
        "北京外国语大学",
        "南昌大学",
        "中国传媒大学",
        "太原理工大学",
        "北京工业大学",
        "空军军医大学",
        "江南大学",
        "陕西师范大学",
        "中国矿业大学",
        "对外经济贸易大学",
        "福州大学",
        "北京林业大学",
        "中国石油大学（华东）",
        "安徽大学",
        "长安大学",
        "辽宁大学",
        "贵州大学",
        "中央财经大学",
        "北京体育大学",
        "河北工业大学",
        "北京中医药大学",
        "东北农业大学",
        "东北林业大学",
        "四川农业大学",
        "中国药科大学",
        "新疆大学",
        "中国矿业大学（北京）",
        "中国地质大学（北京）",
        "天津医科大学",
        "上海外国语大学",
        "大连海事大学",
        "广西大学",
        "华北电力大学",
        "内蒙古大学",
        "海南大学",
        "石河子大学",
        "宁夏大学",
        "延边大学",
        "中央音乐学院",
        "西藏大学",
        "青海大学"
    ]
}


class InformationDisplay:
    def __init__(self):
        self.irmb = ExtractedResumeManageBiz()

    def exec(self, resumecode):
        resume_details = self.get_data(resumecode)
        return self.present(resume_details)

    def get_data(self, resumecode):
        resume = self.irmb.findExtractedResumeByOriginResumeid(resumecode)
        read_path = os.path.join(outputfile, "{}.json".format(resume.getREid()))
        with open(read_path, "r", encoding='utf-8') as f:
            resume_details = json.load(f)
        return resume_details

    def present(self, resume_details):
        data = {
            "msg": "success",
            "code": 200,
            "data": resume_details
        }
        return data


class InformationStatistics:
    def __init__(self):
        self.irmb = ExtractedResumeManageBiz()
        self.rmb = ResumeManageBiz()

    def exec(self, user_id, code_list=None):
        if code_list is None:
            resumes = self.get_data_by_user_id(user_id)
        else:
            resumes = self.get_data(code_list)
        result = self.execute_stat(resumes)
        return self.present(result)

    def get_data(self, code_list):
        resumes = []
        for code in code_list:
            resume = self.irmb.findExtractedResumeByOriginResumeid(code)
            if resume is not None:
                resumes.append(resume)
        return resumes

    def get_data_by_user_id(self, user_id):
        resumes = self.rmb.findAllResumeByRecruiterid(user_id)
        iresumes = []
        for resume in resumes or []:
            iresume = self.irmb.findExtractedResumeByOriginResumeid(resume.getResumeId())
            if iresume is not None:
                iresumes.append(iresume)
        return iresumes

    def execute_stat(self, resumes):
        result = {
            "cities": {

            },
            "age_part": {
                "20_25": 0,
                "25_30": 0,
                "30+": 0
            },
            "degree_part": {
                "本科以下": 0,
                "本科": 0,
                "研究生": 0,
                "博士": 0
            },
            "school": {
                "985类": 0,
                "211类": 0,
                "其他类": 0
            },
            "years": {
                "0_3": 0,
                "3_6": 0,
                "6+": 0
            }
        }

        # 创建一个空字典来保存地址的统计信息
        address_count = {}
        for resume in resumes:
            age = resume.getAge()
            degree = resume.getDegree() or ""
            school = resume.getSchool()
            years = resume.getYears()
            address = resume.getAddress()

            if address and address not in ('无', '未识别'):
                if address in address_count:
                    address_count[address] += 1
                else:
                    address_count[address] = 1

            # 统计年龄部分
            if age is not None and 20 <= age <= 25:
                result["age_part"]["20_25"] += 1
            elif age is not None and 25 < age <= 30:
                result["age_part"]["25_30"] += 1
            elif age is not None and age > 30:
                result["age_part"]["30+"] += 1

            # 统计学历部分

            if "本科" in degree or "学士" in degree:
                result["degree_part"]["本科"] += 1
            elif "硕士" in degree or "研究生" in degree:
                result["degree_part"]["研究生"] += 1
            elif "博士" in degree:
                result["degree_part"]["博士"] += 1
            else:
                result["degree_part"]["本科以下"] += 1

            # 统计学校部分
            if school in school_data["985学校"]:
                result["school"]["985类"] += 1
            elif school in school_data["211学校"]:
                result["school"]["211类"] += 1
            else:
                result["school"]["其他类"] += 1

            # 统计工作年限部分
            if years is not None and 0 <= years <= 3:
                result["years"]["0_3"] += 1
            elif years is not None and 3 < years <= 6:
                result["years"]["3_6"] += 1
            elif years is not None and years > 6:
                result["years"]["6+"] += 1

        address_data = []
        for k, v in address_count.items():
            address_data.append({"name": k, "value": v})
        result['cities'] = address_data
        return result

    def present(self, result):
        data = {
            "msg": "success",
            "code": 200,
            "data": result
        }
        return data
