# Java + AI Agent 后端

2026-09-20：核心开发链路已运行，不是生产交付完成。行为要求见 [行为基准](../MIGRATION_BASELINE.md)，任务状态见 [执行计划](../migration/plan.md)，测试证据见 [执行记录](../migration/execution-report.md)。旧 Django 和数据保留；两端 Vue 默认 API 已改为 Java 8080。

## 已验证与未验证

- JDK 21、Spring Boot 3.5.16、Spring AI 1.1.8；PostgreSQL 16 + pgvector(1024)、Flyway V1-V5。实际持久化使用 JdbcTemplate/TransactionTemplate；JPA 依赖保留，尚无实体映射。
- BCrypt、HttpOnly Session、CSRF、用户/管理员权限；注册密码至少10字符且 UTF-8 不超过72字节。旧令牌不能复用。
- TXT/PDF/DOCX 上传、真实模型抽取、证据向量、去重、详情、统计、版本纠错及删除清理队列。
- JD 解析/确认/删除；工龄、学历、技能过滤与关键词/向量检索、候选人合并；真实工具选择、多轮补查、引用验证、面试问题；无自动录用或拒绝。
- 纠错生成新版向量证据，明确标记人工校对来源；历史原文保留，但不用于新版匹配。V4已有纠错记录需重新保存以生成新版证据。
- 后台任务、SSE、取消、澄清入口、租约及最多两次执行尝试；报告草稿、人工审批和版本过期。
- Vue `/agent` 工作台已接入，桌面/移动端及用户浏览器核心链路通过；管理员业务通过真实 HTTP 测试，管理员浏览器完整流程仍待验收。

最新 `test-real`：22项、0失败、0错误、0跳过（B04证据）。`test` 非AI套件26项、0失败、0错误，4项AI用例跳过，包含无模型上传失败矩阵与Agent预算/工具白名单单元测试（B05）。真实澄清续跑、租约恢复/耗尽SSE通知已覆盖。Chat JSON、Embedding 和多轮工具链均经 Java 客户端调用真实服务。只发送合成资料，未上传已有个人简历。具名强制 tool_choice 不受当前服务支持，使用已验证的 auto 模式。草稿必须先成功检索；候选人必须已召回且读过证据，有召回结果时不允许提交空草稿。

Docker 镜像与数据库阻塞已解除。独立数据库绑定55432，卷名 `iras-java-agent_iras-agent-postgres`。启动命令：

```console
docker pull pgvector/pgvector:pg16
docker compose --env-file java-backend/.env.real -f java-backend/compose.yaml up -d --wait database
```

这些命令不依赖 PowerShell 脚本执行权限，也不会把数据库密码写入命令行。未使用其他项目的数据库容器或未经确认的镜像源。

## 本地配置

本机已创建被 Git 忽略的 `.env.real`。请直接在本机编辑，不要把密钥发到对话中。`.env.real.example` 是无密钥字段模板。

- `DATABASE_PASSWORD`：为本项目独立数据库设置强密码。Compose 与 Java 共用此值。
- `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL`：真实聊天模型；需要支持工具调用和 JSON 输出。base URL 不带 `/chat/completions`，当前示例为服务根地址。
- `EMBEDDING_API_KEY` / `EMBEDDING_BASE_URL` / `EMBEDDING_MODEL`：独立向量服务。不能把聊天模型名当作 Embedding 模型。
- 数据库地址默认 `jdbc:postgresql://127.0.0.1:55432/iras_agent`，账号 `iras_agent`，与旧 SQLite/MySQL 无关。
- 配置为一行一个 `KEY=value`，支持值两端引号，不支持变量替换或行尾注释；进程环境变量优先。
- `COOKIE_SECURE=false` 仅用于本地 HTTP；生产必须 HTTPS 并设为 true。
- `AUTH_REQUESTS_PER_MINUTE=30`：登录/注册按连接IP共享60秒固定窗口，超限429及Retry-After。内存最多10000个来源，满容量对新来源拒绝；重启清零，不是分布式限流。禁用转发头推断，代理后的请求共享代理IP额度，生产需可信网关限流设计。

## 开发命令

在仓库根目录执行。需要 Node 支持 `util.parseEnv`、JDK21 和 Maven；支持 `JAVA21_HOME`、`MAVEN_HOME` 覆盖路径。

```console
node java-backend/dev.mjs check
node java-backend/dev.mjs test
node java-backend/dev.mjs test-real
node java-backend/dev.mjs run
```

`check` 只输出配置是否存在；两种测试都需要真实 PostgreSQL。`test` 跳过模型用例，`test-real` 显式调用真实模型并可能产生费用。测试创建随机 schema 和临时目录，完成后仅清理自己的资源。`run` 构建可执行 jar 并启动完整服务。

另外两个终端启动前端：

```console
npm --prefix vue-ending-project run dev -- --host 127.0.0.1 --port 5173 --strictPort
npm --prefix vue-admin run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

用户端 http://127.0.0.1:5173/agent，管理端 http://127.0.0.1:5174/，健康检查 http://127.0.0.1:8080/api/health。浏览器统一使用127.0.0.1，避免与localhost混用导致Cookie失效。两端共用后端会话，切换登录会替换身份；服务重启需重新登录。

无默认管理员。执行 `node java-backend/dev.mjs admin`，在本机交互式终端输入新账号及隐藏密码；不会提升已有用户或重设密码。该交互命令尚未完成人工验收。不要把密码发送到对话中。

原 `run.ps1` 仍保留，但本机执行策略阻止运行，未绕过策略；现在可使用 Node 入口。

数据库已有数据卷时改变环境密码不会重设库内密码，应执行显式密码轮换。不要使用 `docker compose down -v` 清除数据。后续测试只用独立测试数据并记录清理范围，不对旧数据进行删除操作。

## 边界与剩余工作

旧库只读审计与合成测试：

```console
node --test java-backend/tools/legacy-audit.test.mjs
node java-backend/tools/legacy-audit.mjs mysite/db.sqlite3
```

本机旧SQLite四张业务表均为0行，不需要导入现存数据库记录；个人文件与SQL导出不是已迁入的数据。审计不读取密码字段，不调用模型、不写旧库；真实非空迁移必须先确认身份映射、密码重置和个人数据授权。

停止所有Java写入实例后，执行配套备份；`--quiesced` 表示操作者已确认所有写入停止，工具额外检查本地端口但不能发现其他机器的写入者：

```console
node java-backend/tools/backup.mjs create --quiesced
node java-backend/tools/backup.mjs verify java-backend/backups/<快照目录>
```

工具仅用于本地Compose的iras_agent数据库，备份包含私密数据，应保护目录。verify只恢复到随机临时库并删除该库，不覆盖运行库；SHA256与文件引用核对通过后仍需在计划停机中执行正式恢复。停写创建快照已验证0份简历；`node --test java-backend/tools/backup.test.mjs` 使用真实临时库、V1-V5及1份合成文件，验证恢复成功、损坏拒绝和缺失引用拒绝。它不代替生产恢复或非空停写创建验收。CI位于 [工作流](../.github/workflows/java-agent.yml)，尚未远端执行，付费AI测试仅手工开启。

- 兼容上传为同步流程：1-5份、10MB/份、50000字符；PDF最多100页。无OCR，扫描件明确失败；DOCX页码0表示无法确定，paragraph当前为1600字符片段序号。
- 工具预算8次、轮次5轮、任务预算180秒。取消阻止后续步骤与落库，但在途网络调用可能等到60秒读取超时。租约恢复只重放安全读步骤，真实进程中断演练尚未完成。
- 引用归属及原文存在性由代码校验；引用是否足以支持岗位要求仍需人工核对，检索分数不是能力分数。
- 管理员浏览器全流程、完整故障回归、全量依赖扫描、标注集效果评测、远端CI及生产部署仍待完成。未测准确率或Recall@K。B04已扫描33个后端关键依赖，49条公告经补丁升级后同范围复扫为0；不包含全部传递依赖、npm及容器，不能声称全项目无漏洞。
- 已有单实例认证限流；生产网关限流、全量脱敏审计和上传崩溃孤儿文件对账仍待完成。前端调试日志和无依据模型宣传已清理，但不等于完成全面隐私审计。
- 文件默认在 `java-backend/data/resumes`。生产需要HTTPS、Secure Cookie、严格CORS与秘密管理；禁止以 `down -v` 代替迁移或密码轮换。
- 冻结式详细边界测试包尚未形成。OCR、外部通知与排期未实现，不会假装执行成功。