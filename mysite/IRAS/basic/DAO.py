class IRecruiterDao:
    @staticmethod
    def findRecruiterByAccount(account):
        pass

    @staticmethod
    def findRecruiterById(id):
        pass

    @staticmethod
    def findPwdByAccount(account):
        pass

    @staticmethod
    def findAllAccount():
        pass

    @staticmethod
    def checkRecruiterByAccount(account):
        pass

    @staticmethod
    def addRecruiter(account, password):
        pass

    @staticmethod
    def updateRecruiterPwdById(id, password):
        pass

    @staticmethod
    def deleteRecruiterById(id):
        pass


class IAdministratorDao:
    @staticmethod
    def findAdminByAccount(account):
        pass

    @staticmethod
    def findPwdByAccount(account):
        pass

    @staticmethod
    def checkAdministratorByAccount(account):
        pass

    @staticmethod
    def addAdministrator(account, password):
        pass

    @staticmethod
    def deleteAdministratorById(id):
        pass


class IResumeDao:
    @staticmethod
    def findAllResumeByRecruiterid(id):
        pass

    @staticmethod
    def findAllResume():
        pass

    @staticmethod
    def findResumeById(resumeid):
        pass

    @staticmethod
    def addResume(resumename, resumeurl, recruiterid=None):
        pass

    @staticmethod
    def deleteResumeById(resumeid):
        pass

    @staticmethod
    def deleteAllResumeByRecruiterId(id):
        pass


class IExtractedResumeDao:
    @staticmethod
    def findExtractedResumeById(reid):
        pass

    @staticmethod
    def findExtractedResumeByOriginResumeid(resumeid):
        pass

    @staticmethod
    def addExtractedResumeById(resumeid, resumename, name, age, degree, school, address, years):
        pass

    @staticmethod
    def deleteResumeById(reid):
        pass
