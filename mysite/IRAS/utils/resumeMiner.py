# -*- coding: utf-8 -*-

import os
import re

from pdfminer.pdfparser import PDFParser
from pdfminer.pdfdocument import PDFDocument
from pdfminer.pdfpage import PDFPage
from pdfminer.pdfinterp import PDFResourceManager, PDFPageInterpreter
from pdfminer.converter import PDFPageAggregator
from pdfminer.layout import LTTextBoxHorizontal, LAParams

base_dir = "./files"


class myCeilContent:
    def __init__(self, ylen, x):
        self.x0 = x.x0
        self.x1 = x.x1
        self.y0 = ylen - x.y1
        self.y1 = ylen - x.y0
        self.text = re.sub("[' ']+", '', x.get_text())
        self.text = re.sub('\n+', ' ', self.text)
        self.text = re.sub('◼|⚫||', '', self.text)


class FileTextExtraction:
    def __init__(self):
        self.resumeName = None
        self.resumeUrl = None
        self.pdf = None
        self.source = ""

    def exec(self, resume):
        self.resumeName = resume.getResumeName()
        self.resumeUrl = os.path.join(base_dir, resume.getUrl())
        self.source = ""
        with open(self.resumeUrl, 'rb') as pdf:
            self.pdf = pdf
            self._parse_pdfminer()
            return self.source

    def exec_for_text(self, resume):
        self.resumeName = resume.getResumeName()
        self.resumeUrl = os.path.join(base_dir, resume.getUrl())
        with open(self.resumeUrl, 'r', encoding='utf-8') as text:
            source = ""
            line = text.readline()
            while line:
                source += line
                line = text.readline()
            return source

    def _merge_interval(self, intervals):
        intervals.sort()
        result = []
        if len(intervals) == 0:
            return []
        result.append(intervals[0])
        for i in range(1, len(intervals)):
            if intervals[i][0] < result[-1][1]:
                result[-1][1] = max(intervals[i][1], result[-1][1])
            else:
                result.append(intervals[i])
        return result

    def _divide_page(self, layout):
        leftcontent = []
        rightcontent = []
        intervals = []
        for item in layout:
            if isinstance(item, LTTextBoxHorizontal):
                intervals.append([item.x0, item.x1])
        result = self._merge_interval(intervals)
        if len(result) == 1:
            return 1
        elif len(result) == 2:
            divide_mid_line = (result[0][1] + result[1][0]) / 2
            for item in layout:
                if isinstance(item, LTTextBoxHorizontal):
                    if item.x1 < divide_mid_line:
                        leftcontent.append(myCeilContent(layout.y1, item))
                    else:
                        rightcontent.append(myCeilContent(layout.y1, item))
            leftcontent.sort(key=lambda obj: (obj.y1, obj.x0))
            rightcontent.sort(key=lambda obj: (obj.y1, obj.x0))
            llen = len(leftcontent)
            rlen = len(rightcontent)
            if llen < 10 or rlen < 10:
                return 1
            elif llen <= rlen / 3 or rlen <= llen / 3:
                return 1
            leftsource = ""
            rightsource = ""
            pre_ceil = None
            for ceil in leftcontent:
                if self._same_line_judge(pre_ceil, ceil):
                    leftsource += ' ' + ceil.text
                else:
                    leftsource += '\n' + ceil.text
                pre_ceil = ceil
            pre_ceil = None
            for ceil in rightcontent:
                if self._same_line_judge(pre_ceil, ceil):
                    rightsource += ' ' + ceil.text
                else:
                    rightsource += '\n' + ceil.text
                pre_ceil = ceil
            self.source += leftsource + '\n' + rightsource + '\n'
            return 2
        else:
            return 1

    def _same_line_judge(self, x1, x2):
        if x1 is None:
            return True
        if x1.y0 == x2.y0:
            return True

    def _parse_pdfminer(self):
        # 用文件指针创建一个PDF文档分析器
        parser = PDFParser(self.pdf)
        # 创建PDF文档
        doc = PDFDocument(parser)
        # 创建PDF资源管理器，来管理共享资源
        pdf_resource_manager = PDFResourceManager()
        # 创建一个PDF设备对象
        pdf_la_params = LAParams()
        # 将资源管理器和设备对象聚合
        device = PDFPageAggregator(pdf_resource_manager, laparams=pdf_la_params)
        # 创建一个PDF解释器对象
        interpreter = PDFPageInterpreter(pdf_resource_manager, device)
        # 按照页进行遍历
        for i, page in enumerate(PDFPage.create_pages(doc)):
            # 使用页面解释器来读取
            interpreter.process_page(page)
            # 接收该页面的LTPage对象，包含有解析出来的各种对象
            layout = device.get_result()
            split_num = self._divide_page(layout)
            if split_num == 1:
                content = []
                fsource = ""
                for item in layout:
                    if isinstance(item, LTTextBoxHorizontal):
                        content.append(myCeilContent(layout.y1, item))
                content.sort(key=lambda obj: (obj.y0, obj.x0))
                pre_ceil = None
                for ceil in content:
                    if self._same_line_judge(pre_ceil, ceil):
                        fsource += ' ' + ceil.text
                    else:
                        fsource += '\n' + ceil.text
                    pre_ceil = ceil
                self.source += fsource + '\n'
