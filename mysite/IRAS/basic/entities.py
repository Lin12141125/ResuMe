class Recruiter:
    def __init__(self, id, account, password):
        self.__id = id
        self.__account = account
        self.__password = password

    def getPwd(self):
        return self.__password

    def getAccount(self):
        return self.__account

    def getId(self):
        return self.__id


class Administrator:
    def __init__(self, id, account, password):
        self.__id = id
        self.__account = account
        self.__password = password

    def getPwd(self):
        return self.__password

    def getAccount(self):
        return self.__account

    def getId(self):
        return self.__id


class Resume:
    def __init__(self, resumeid, resumename, resumeurl, recruiterid):
        self.__resumeid = resumeid
        self.__resumename = resumename
        self.__resumeurl = resumeurl
        self.__recruiterid = recruiterid

    def getResumeId(self):
        return self.__resumeid

    def getResumeName(self):
        return self.__resumename

    def getUrl(self):
        return self.__resumeurl

    def getRecruiterId(self):
        return self.__recruiterid


class ExtractedResume:
    def __init__(self, reid, resumeid, resumename, name, age, degree, school, address, years):
        self.__reid = reid
        self.__resumeid = resumeid
        self.__resumename = resumename
        self.__name = name
        self.__age = age
        self.__degree = degree
        self.__school = school
        self.__address = address
        self.__years = years

    def getREid(self):
        return self.__reid

    def getResumeId(self):
        return self.__resumeid

    def getResumeName(self):
        return self.__resumename

    def getName(self):
        return self.__name

    def getAge(self):
        return self.__age

    def getDegree(self):
        return self.__degree

    def getSchool(self):
        return self.__school

    def getAddress(self):
        return self.__address

    def getYears(self):
        return self.__years
