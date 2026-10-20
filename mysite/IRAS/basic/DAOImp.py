from .DAO import *

from IRAS.models import *


class RecruiterDao(IRecruiterDao):
    @staticmethod
    def findRecruiterByAccount(account):
        obj = RiesRecruiter.objects.filter(recruiter_account=account).first()
        return obj

    @staticmethod
    def findRecruiterById(id):
        obj = RiesRecruiter.objects.filter(recruiter_id=id).first()
        return obj

    @staticmethod
    def findPwdByAccount(account):
        obj = RiesRecruiter.objects.filter(recruiter_account=account).first()
        return obj.password

    @staticmethod
    def findAllAccount():
        queryset = RiesRecruiter.objects.all()
        return queryset

    @staticmethod
    def checkRecruiterByAccount(account):
        obj = RiesRecruiter.objects.filter(recruiter_account=account).first()
        return obj

    @staticmethod
    def addRecruiter(account, password):
        obj = RiesRecruiter.objects.create(recruiter_account=account, password=password)
        return obj

    @staticmethod
    def updateRecruiterPwdById(id, password):
        RiesRecruiter.objects.filter(recruiter_id=id).update(password=password)
        obj = RiesRecruiter.objects.filter(recruiter_id=id).first()
        return obj

    @staticmethod
    def deleteRecruiterById(id):
        obj = RiesRecruiter.objects.filter(recruiter_id=id).delete()


class AdministratorDao(IAdministratorDao):
    @staticmethod
    def findAdminByAccount(account):
        obj = RiesAdministrator.objects.filter(admin_account=account).first()
        return obj

    @staticmethod
    def findPwdByAccount(account):
        obj = RiesAdministrator.objects.filter(admin_account=account).first()
        return obj.password

    @staticmethod
    def checkAdministratorByAccount(account):
        obj = RiesAdministrator.objects.filter(admin_account=account).first()
        return obj

    @staticmethod
    def addAdministrator(account, password):
        obj = RiesAdministrator.objects.create(admin_account=account, password=password)
        return obj

    @staticmethod
    def deleteAdministratorById(id):
        obj = RiesAdministrator.objects.filter(admin_id=id).delete()


class ResumeDao(IResumeDao):
    @staticmethod
    def findAllResumeByRecruiterid(id):
        queryset = RiesResume.objects.filter(recruiter_id=id).all()
        return queryset

    @staticmethod
    def findAllResume():
        queryset = RiesResume.objects.all()
        return queryset

    @staticmethod
    def findResumeById(resumeid):
        obj = RiesResume.objects.filter(resume_id=resumeid).first()
        return obj

    @staticmethod
    def addResume(resumename, resumeurl, recruiterid=None):
        obj = RiesResume.objects.create(
            resume_name=resumename, recruiter_id=recruiterid
        )
        resumeid = obj.resume_id
        url = resumeurl.format(str(resumeid))
        RiesResume.objects.filter(resume_id=resumeid).update(resume_url=url)
        obj = RiesResume.objects.filter(resume_id=resumeid).first()
        return obj

    @staticmethod
    def deleteResumeById(resumeid):
        deleted, _ = RiesResume.objects.filter(pk=resumeid).delete()
        return deleted > 0

    @staticmethod
    def deleteAllResumeByRecruiterId(id):
        obj = RiesResume.objects.filter(recruiter_id=id).delete()


class ExtractedResumeDao(IExtractedResumeDao):
    @staticmethod
    def findExtractedResumeById(reid):
        obj = RiesResumeExtracted.objects.filter(resume_extracted_id=reid).first()
        return obj

    @staticmethod
    def findExtractedResumeByOriginResumeid(resumeid):
        obj = RiesResumeExtracted.objects.filter(resume_id=resumeid).first()
        return obj

    @staticmethod
    def addExtractedResumeById(
        resumeid, resumename, name, age, degree, school, address, years
    ):
        obj = RiesResumeExtracted.objects.create(
            resume_id=resumeid,
            resume_name=resumename,
            name=name,
            age=age,
            degree=degree,
            school=school,
            address=address,
            years=years,
        )
        return obj

    @staticmethod
    def deleteResumeById(reid):
        return RiesResumeExtracted.objects.filter(pk=reid).delete()
