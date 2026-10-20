# -*- coding: utf-8 -*-
import math
import re

currentTime = {'year': 2023, 'month': 4}


class TextFiltering:
    def __init__(self):
        pass

    def depart_time(self, timing):
        timing = str(timing)
        if timing == "至今":
            year = currentTime['year']
            month = currentTime['month']
        elif re.search(r'\.', timing) is not None:
            year = int(timing.split(".")[0])
            month = int(timing.split(".")[1])
        else:
            year = int(timing)
            month = 0
        return year, month

    def _work_exp_filter(self, lists, parts):
        remove_index_list = []
        shuffled_lists = []
        for i in range(len(lists)):
            pattern = re.compile(r'实习')
            if re.search(pattern, parts[i + 1]) is not None:
                remove_index_list.append(i)
        for i in range(len(lists)):
            if i not in remove_index_list:
                shuffled_lists.append(lists[i])
        return shuffled_lists

    def cal_work_exp(self, mstr):
        work_exp_lists = []
        patternCatch = re.compile(r"(?:20|19)\d{2}(?:\.\d+)?(?:\s|-|–)*(?:至今|(?:20|19)\d{2}(?:\.\d+)?)")
        m = patternCatch.findall(mstr)
        parts = re.split(patternCatch, mstr)
        if len(m) == 0:  # 中文特判
            mstr = re.sub(r'到', ' ', mstr)
            mstr = re.sub(r'\s+', ' ', mstr)
            m = patternCatch.findall(mstr)
            parts = re.split(patternCatch, mstr)
        if len(m) == 0:
            patternCatch = re.compile(r"(?:\d+\.)?(?:20|19)\d{2}(?:\s|-|–)*(?:至今|(?:\d+\.)?(?:20|19)\d{2})")
            m = patternCatch.findall(mstr)
            parts = re.split(patternCatch, mstr)
        if len(m) == 0:
            patternCatch = re.compile(r"(?:20|19)\d{2}年(?:\d+月)?(?:\s|-|–)*(?:至今|(?:20|19)\d{2}年(?:\d+月)?)")
            m = patternCatch.findall(mstr)
            parts = re.split(patternCatch, mstr)
        m = self._work_exp_filter(m, parts)  # 进一步筛查是否含有实习经历
        for item in m:
            item = re.sub(r"年", '.', item)
            item = re.sub(r"月", '', item)
            item = re.sub(r"[' ']+", '-', item)
            item = re.sub(r"[-]+", '-', item)
            item = re.sub(r"[–]+", '-', item)
            item = item.strip('.')
            if re.search('至今', item) is not None:
                start = re.findall(r'\d{4}(?:\.\d+)?', item)[0]
                end = '至今'
            else:
                start = re.findall(r'(.*)(?:\s|-)+(.*)', item)[0][0]
                end = re.findall(r'(.*)(?:\s|-)+(.*)', item)[0][1]
            startPart = self.depart_time(start)
            endPart = self.depart_time(end)
            itemMonth = (endPart[0] - startPart[0]) * 12 + (endPart[1] - startPart[1])
            work_exp_lists.append((item, itemMonth))
        return work_exp_lists

    def get_work_block(self, content):
        startpattern = re.compile('工作经历|工作经验|experience', re.I)
        endpattern = re.compile("项目经历|教育背景|奖项荣誉|项目经验|基本信息|校内实践|技能证书|掌握技能|获得证书|参与事迹|获奖经历", re.I)
        blockcontent = ''
        beginMark = False
        lines = content.split('\n')
        for line in lines:
            if not beginMark:
                if re.search(startpattern, line) is not None:
                    beginMark = True
                    blockcontent += line
            else:
                if re.search(endpattern, line) is None:
                    blockcontent += line
                else:
                    beginMark = False
        return blockcontent

    def exec(self, content):
        work_exp_year = 0
        if re.search('工作经历|工作经验|experience', content, re.I) is not None:
            block_content = self.get_work_block(content)
            work_exp_list = self.cal_work_exp(block_content)
            for work_exp in work_exp_list:
                work_exp_year += work_exp[1]
            work_exp_year = math.ceil(work_exp_year / 12)
        else:
            work_exp_year = 0

        return work_exp_year

# input
# resumecode简历编号, resumename简历名称, str文本内容, year工作年限
# ouput
# {
#   "resumecode":1,
#   "resumename":"小明简历",
#   "name":"小明",
#   "age":24,
#   "degree":"本科",
#   "school":"重庆大学",
#   "years":2
# }

# input InterpretedResume类：ircode经解析简历编号 resumecode简历编号, resumename简历名称, name名字，age年龄，degree学历，school毕业院校，years工作年限
# output
# {
#     "age_part": {
#         "20_25": 5,
#         "25_30": 5,
#         "30+": 5,
#     },
#     "degree_part": {
#         "本科以下": 5,
#         "本科": 5,
#         "研究生": 5,
#         "研究生以上": 5,
#     },
#     "school": {
#         "985类": 5,
#         "211类": 5,
#         "其他类": 5,
#     },
#     "years": {
#         "0_3": 5,
#         "3_6": 5,
#         "6+": 5,
#     }
# }
