from django.urls import path, include
from . import views

urlpatterns = [
    path('', views.common),
    path('UserRegister/', views.user_register),
    path('UserLogin/', views.user_login),
    path('ResumeUpload2Interpret/', views.resume_upload2interpret),
    path('SingleResumePresent/', views.single_resume_present),
    path('ResumeStatistic/', views.resume_statistic),
    path('FindUserResumes/', views.find_user_resumes),
    path('ResumeDelete/', views.resume_delete),
    path('UserDelete/', views.user_delete),
    path('FindAllUsers/', views.find_all_users),
    path('AdminLogin/', views.admin_login),
]
