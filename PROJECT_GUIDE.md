# 简历分析项目检查与运行说明

> 2026-09-19 更新：两端 Vue 默认已接入 Java + Spring AI 后端（8080），新增岗位与 Agent 工作台。当前启动方式和验收边界请先阅读 [Java 说明](java-backend/README.md) 与 [迁移记录](migration/execution-report.md)。以下内容保留为旧 Django 阶段记录，不代表当前 Vue 仍使用旧令牌认证。

## 结论

原项目不能按旧说明可靠地直接运行：依赖/API版本不一致、业务表不能自动迁移、管理端入口使用本机文件路径，上传流程可能失败仍报成功，并存在未鉴权的数据访问。

修复后，本机 Python 3.13 + SQLite + 本地规则模式的核心流程已运行验证。真实大模型调用仍需要自己的有效 API 密钥和可访问的服务；本次未调用外部模型、未上传真实简历。当前是本地开发原型，不是已完成生产验收的系统。

## 技术栈

| 层次 | 技术与作用 |
| --- | --- |
| 用户端 | Vue 3、Vite 5、Vue Router、Pinia、Element Plus、Tailwind CSS、Axios |
| 管理端 | Vue 3、Vite 4、Vue Router、Vuex、Element Plus、Tailwind CSS、Axios |
| 可视化 | ECharts、China.json，展示地区、年龄、学历、学校类别、工作年限分布 |
| 后端 | Python、Django ORM、JSON HTTP API、django-cors-headers；声明了 DRF，但业务接口主要是普通 Django 视图 |
| 数据存储 | SQLite 本地默认；可配置 MySQL/PyMySQL；原始简历保存在 files，结构化结果保存在 files_json |
| PDF | pdfminer.six 提取可复制的文本，不提供 OCR |
| AI | 新版 OpenAI Python SDK 调用 OpenAI-compatible 接口，默认模型配置为 DeepSeek Chat |
| 认证 | 密码哈希、24小时有效签名令牌、服务端角色与简历归属验证 |

它更准确地说是“LLM 驱动的简历信息抽取应用”，不是具备自主规划、工具选择、长期记忆与反馈循环的完整 AI Agent。未发现 LangChain/LangGraph、RAG、向量库、微调训练或图像多模态推理链路。页面旧文案提到的“多模态”不等于代码已经实现 OCR/视觉模型。

## 功能与流程

1. 招聘者注册、登录，上传 UTF-8 文本或文本型 PDF，可批量上传最多5份。
2. 文件校验、PDF文本提取，将文本交给模型，返回中文字段 JSON。
3. 提取姓名、年龄、联系方式、学历、学校、籍贯、技能、奖项、教育与工作经历。
4. 保存原文件、结构化 JSON 和数据库摘要；展示个人简历列表、详情与统计图表。
5. 删除简历时清理数据库关联记录与文件；管理员可登录、查看用户、查看用户简历和删除用户。

链路：Vue/Axios -> Django views -> PDF/文本提取 -> LLM 或显式 local 规则模式 -> ORM + 文件 -> 详情/统计。

local 只识别若干明确标签及电话、邮箱，不生成教育/工作经历或技能分析。llm 模式失败会返回错误，不再悄悄降级成“AI 成功”。文本超过50000字符拒绝处理，不再静默截断前3000字符。

## 已修复

| 问题 | 修复 |
| --- | --- |
| 管理端白屏 | 将 Windows 本机脚本路径改为 Vite 的 /src/main.js |
| SDK与依赖不一致、缺少依赖 | 统一新版 OpenAI，使用支持 Python 3.13 的 Django 5.2，补齐 dotenv，移除未使用依赖 |
| 源码内嵌凭据 | 从活动 Python 实现移除密钥/数据库密码，使用环境变量；开发签名密钥随机生成 |
| 表模型与迁移不一致 | 新增迁移，默认 SQLite 可初始化业务表；保留旧迁移，不删除已有表 |
| 上传未保存原文件、吞异常假成功 | 校验文件，解析后事务写库与文件保存，失败清理新建文件并回滚 |
| PDF接口不兼容 | 将上传对象底层文件流传给 pdfminer，加入真实合成 PDF 测试 |
| 无身份/归属验证、密码泄露 | 密码哈希、令牌鉴权、管理员保护、越权拒绝；不再返回用户密码 |
| 删除绕过外键、危险测试接口 | 使用 ORM 级联，移除 GET 删除测试接口，用户删除改为受保护 POST |
| 工龄写死、空统计崩溃 | 按日期区间合并累计，处理空列表、未解析记录、缺失值 |
| 前端登录拦截错误 | 修复按位或与大小写路径，存储并携带令牌 |
| 上传重试丢数据 | 请求期间禁用按钮，失败保留输入/文件，并展示后端原因 |
| 移动端溢出及组件警告 | 导航收缩、详情表格内部滚动、修正 size 和 el-sub-menu |
| 原测试会删除真实简历 | 改为调用 Django 隔离测试套件 |

## 启动

以下命令都在 code 根目录、Windows CMD 执行。当前已创建 .venv 并初始化 SQLite，不必重复初始化环境。

后端：

```bat
py -3.13 -m venv .venv
.venv\Scripts\python -m pip install -r mysite\requirements.txt
.venv\Scripts\python mysite\manage.py migrate
set "RESUME_PARSER=local"
.venv\Scripts\python mysite\manage.py runserver 127.0.0.1:8000
```

用户端另开终端：

```bat
npm --prefix vue-ending-project install
npm --prefix vue-ending-project run dev -- --host 127.0.0.1 --port 5173
```

管理端另开终端：

```bat
npm --prefix vue-admin install
npm --prefix vue-admin run dev -- --host 127.0.0.1 --port 5174
```

访问用户端 http://127.0.0.1:5173 ，管理端 http://127.0.0.1:5174 ，后端状态 http://127.0.0.1:8000/IRAS/ 。用户端可自行注册；管理员使用 `createirasadmin` 命令创建，详见 [后端说明](mysite/Readme.md)。无默认管理员或测试口令。

VS Code 也提供 Build user frontend、Build admin frontend、Verify backend、Run local backend 任务。

## AI 与配置

配置样例见 [mysite/.env.example](mysite/.env.example)。在 mysite/.env 设置自己的配置；不要将密钥写入代码或提交到仓库。

```dotenv
RESUME_PARSER=llm
OPENAI_API_KEY=your-own-key
OPENAI_BASE_URL=https://api.deepseek.com/v1
OPENAI_MODEL=deepseek-chat
```

OpenAI 服务可将地址改为 https://api.openai.com/v1，并选择账号可用、支持 JSON 输出的模型。先清除 CMD 中旧的 `RESUME_PARSER` 再重启。若更改前端 API 地址，在对应前端 .env.local 设置 `VITE_API_BASE_URL`；对应来源还须加入后端 `CORS_ALLOWED_ORIGINS`。

建议设置随机 `DJANGO_SECRET_KEY`；未设置时开发服务器每次重启会使旧令牌失效，需要重新登录。`DJANGO_DEBUG=0` 时必须显式设置此密钥。

## 验证与限制

验证详情见 [runtime-validation-report.md](runtime-validation-report.md)。12 个后端测试通过、两端生产构建通过、数据库迁移一致；浏览器验证了用户注册、上传、列表、详情、统计和管理员登录/用户列表。没有对真实 AI 服务、旧 MySQL 数据迁移或全部页面做完整验收。

- 原源码出现过的 API 密钥和数据库密码应视为已暴露，请在对应服务撤销/轮换；删除源码内容不能撤销凭据。
- 旧 SQL 的解析表与当前模型不同。迁移只创建缺失表，不自动转换已有旧表/数据/JSON，先备份再规划迁移。
- 不支持扫描 PDF 的 OCR、DOCX、图像简历、异步任务队列、岗位匹配评分或完整 Agent 工具编排。
- 上传及 AI 调用同步执行，批量耗时较长；没有后台进度、限流、配额控制或多进程账号注册竞争保护。
- 统计使用简化年龄/学历分段、固定学校名单和摘要字段，不能作为招聘决策依据；未知学历目前归入本科以下、未知学校归入其他类。
- 前端还有演示用链接、旧营销文案、分页仅刷新未切片等原型行为；不是全部功能完整验收。
- 生产部署仍需 HTTPS、生产 WSGI/ASGI 服务、强密码策略、登录限流、日志/备份/隐私保留策略、依赖安全扫描与令牌撤销方案。当前令牌保存在浏览器 localStorage，应评估 XSS 风险。
- 构建存在较大的 JS 包和旧 Browserslist 数据提示；没有以性能优化或全依赖安全审计作为本次通过标准。