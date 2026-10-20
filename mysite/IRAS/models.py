# This is an auto-generated Django model module.
# You'll have to do the following manually to clean this up:
#   * Rearrange models' order
#   * Make sure each model has one field with primary_key=True
#   * Make sure each ForeignKey and OneToOneField has `on_delete` set to the desired behavior
#   * Remove `managed = False` lines if you wish to allow Django to create, modify, and delete the table
# Feel free to rename the models, but don't rename db_table values or field names.
from django.db import models


class RiesAdministrator(models.Model):
    admin_id = models.AutoField(primary_key=True, db_column='adminId')
    admin_account = models.CharField(max_length=60, db_column='adminAccount')
    password = models.CharField(max_length=100)
    
    class Meta:
        managed = True
        db_table = 'iras_t_administrator'


class RiesRecruiter(models.Model):
    recruiter_id = models.AutoField(primary_key=True, db_column='recruiterId')
    recruiter_account = models.CharField(max_length=60, db_column='recruiterAccount')
    password = models.CharField(max_length=100)
    
    class Meta:
        managed = True
        db_table = 'iras_t_recruiter'


class RiesResume(models.Model):
    resume_id = models.AutoField(primary_key=True, db_comment="简历编号", db_column='resumecode')
    resume_name = models.CharField(max_length=60, db_comment="简历名称", db_column='resumename')
    resume_url = models.CharField(max_length=255, db_comment="简历文件存储地址", db_column='resumeurl')
    recruiter = models.ForeignKey(
        RiesRecruiter,
        models.CASCADE,
        blank=True,
        null=True,
        db_comment="所归属招聘人员编号",
        db_column='recruiterId'  # 添加这一行
    )

    class Meta:
        managed = True
        db_table = 'iras_t_resume'


class RiesResumeExtracted(models.Model):
    resume_extracted_id = models.AutoField(primary_key=True)
    resume = models.ForeignKey(RiesResume, models.CASCADE)
    resume_name = models.CharField(max_length=60)
    name = models.CharField(max_length=100)
    age = models.IntegerField(blank=True, null=True)
    degree = models.CharField(max_length=16, blank=True, null=True)
    school = models.CharField(max_length=16, blank=True, null=True)
    address = models.CharField(max_length=160, blank=True, null=True)
    years = models.IntegerField(blank=True, null=True)

    class Meta:
        managed = True
        db_table = "ries_resume_extracted"
