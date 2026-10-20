from django.db import migrations, models
import django.db.models.deletion


def create_missing_tables(apps, schema_editor):
    existing_tables = schema_editor.connection.introspection.table_names()
    for model_name in ("RiesAdministrator", "RiesRecruiter", "RiesResume", "RiesResumeExtracted"):
        model = apps.get_model("IRAS", model_name)
        if model._meta.db_table not in existing_tables:
            schema_editor.create_model(model)


class Migration(migrations.Migration):
    dependencies = [("IRAS", "0001_initial")]

    operations = [
        migrations.SeparateDatabaseAndState(state_operations=[
            migrations.DeleteModel(name="RiesResumeExtracted"),
            migrations.DeleteModel(name="RiesResume"),
            migrations.DeleteModel(name="RiesRecruiter"),
            migrations.DeleteModel(name="RiesAdministrator"),
            migrations.CreateModel(
                name="RiesAdministrator",
                fields=[
                    ("admin_id", models.AutoField(primary_key=True, serialize=False, db_column="adminId")),
                    ("admin_account", models.CharField(max_length=60, db_column="adminAccount")),
                    ("password", models.CharField(max_length=100)),
                ],
                options={"db_table": "iras_t_administrator", "managed": True},
            ),
            migrations.CreateModel(
                name="RiesRecruiter",
                fields=[
                    ("recruiter_id", models.AutoField(primary_key=True, serialize=False, db_column="recruiterId")),
                    ("recruiter_account", models.CharField(max_length=60, db_column="recruiterAccount")),
                    ("password", models.CharField(max_length=100)),
                ],
                options={"db_table": "iras_t_recruiter", "managed": True},
            ),
            migrations.CreateModel(
                name="RiesResume",
                fields=[
                    ("resume_id", models.AutoField(primary_key=True, serialize=False, db_column="resumecode", db_comment="简历编号")),
                    ("resume_name", models.CharField(max_length=60, db_column="resumename", db_comment="简历名称")),
                    ("resume_url", models.CharField(max_length=255, db_column="resumeurl", db_comment="简历文件存储地址")),
                    ("recruiter", models.ForeignKey(to="IRAS.riesrecruiter", on_delete=django.db.models.deletion.CASCADE, blank=True, null=True, db_column="recruiterId", db_comment="所归属招聘人员编号")),
                ],
                options={"db_table": "iras_t_resume", "managed": True},
            ),
            migrations.CreateModel(
                name="RiesResumeExtracted",
                fields=[
                    ("resume_extracted_id", models.AutoField(primary_key=True, serialize=False)),
                    ("resume", models.ForeignKey(to="IRAS.riesresume", on_delete=django.db.models.deletion.CASCADE)),
                    ("resume_name", models.CharField(max_length=60)),
                    ("name", models.CharField(max_length=100)),
                    ("age", models.IntegerField(blank=True, null=True)),
                    ("degree", models.CharField(max_length=16, blank=True, null=True)),
                    ("school", models.CharField(max_length=16, blank=True, null=True)),
                    ("address", models.CharField(max_length=160, blank=True, null=True)),
                    ("years", models.IntegerField(blank=True, null=True)),
                ],
                options={"db_table": "ries_resume_extracted", "managed": True},
            ),
        ]),
        migrations.RunPython(create_missing_tables, migrations.RunPython.noop),
    ]