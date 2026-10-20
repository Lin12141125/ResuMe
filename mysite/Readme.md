## 本地启动

完整技术栈、检查结果和限制见 [项目说明](../PROJECT_GUIDE.md)。以下命令在 code 根目录的 Windows CMD 执行，推荐 Python 3.13。

```bat
py -3.13 -m venv .venv
.venv\Scripts\python -m pip install -r mysite\requirements.txt
.venv\Scripts\python mysite\manage.py migrate
set "RESUME_PARSER=local"
.venv\Scripts\python mysite\manage.py runserver 127.0.0.1:8000
```

默认使用 SQLite，无需安装 MySQL 或导入旧 SQL。local 仅为规则提取演示，不是大模型推理。

真实 AI：在 mysite/.env 中参照 [.env.example](.env.example) 设置 `RESUME_PARSER=llm`、`OPENAI_API_KEY`、`OPENAI_BASE_URL` 和 `OPENAI_MODEL`，重启前先清除 CMD 中旧的 `RESUME_PARSER` 环境变量。进程环境变量优先于 .env。

```bat
set RESUME_PARSER=
.venv\Scripts\python mysite\manage.py runserver 127.0.0.1:8000
```

简历文本将发送到配置的外部 AI 服务，使用前应获得数据主体授权。

管理员需单独创建，密码在终端隐藏输入，无内置默认账号：

```bat
.venv\Scripts\python mysite\manage.py createirasadmin admin
```

回归测试使用独立测试数据库和临时目录：

```bat
.venv\Scripts\python mysite\manage.py test IRAS
```

MySQL 可通过 `DB_ENGINE=mysql` 和 `MYSQL_*` 环境变量启用。已有数据库必须先备份；迁移只补建缺失表，不自动修正已有表或搬迁旧解析数据。旧 [iras.sql](iras.sql) 的 `iras_t_interpretedresume` 与当前模型不同，不能视为完整初始化脚本，且含历史数据，不要在已有库直接执行。

