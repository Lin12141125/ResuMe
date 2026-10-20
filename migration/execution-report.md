# 迁移执行记录

## 2026-09-20 检查点 5（当前）

运行时仍为Java + Spring AI，任务计数保持7 verified / 13 partial / 1 optional。本批补充失败矩阵与Agent预算白名单测试，并恢复因Vite缓存损坏停止的前端。

- 新增 `AgentRunnerBudgetTest`（纯单元，不依赖数据库/模型）：轮次预算耗尽、工具调用预算耗尽、未授权工具名直接拒绝，3项均通过。预算与工具白名单此前只在真实模型路径中间接验证，现在有确定性单元覆盖。
- 新增非AI上传失败矩阵测试（AuthIntegrationTest第10项）：无文件part的空批次400、6文件批400、不支持类型400、空文件400、超50000字符400、伪造owner参数403、匿名上传401，全程不调用模型、不产生数据库记录。
- 非AI完整套件26项通过、0失败、0错误，4项AI用例按预期跳过；Boot3.5.16/AI1.1.8运行。AI真实套件22项证据仍以检查点4为准，本批未重复调用真实模型。
- 修复测试基础设施：上传multipart字节尾缺少CRLF导致解析失败、Spring AI `Generation.getOutput()`等final方法不可stub改用真实构造。
- 前端曾因重复启动损坏Vite依赖缓存（504 Outdated Optimize Dep），已停止项目自身Vite进程（PID42196）、删除`vue-ending-project/node_modules/.vite`并重启，用户端/agent工作台恢复渲染。
- 浏览器验证：用户端未登录跳转登录页、合成账号`synthetic-b05-browser`注册200与me 200、/agent工作台三标签完整渲染；管理端5174未登录守卫到登录页。管理员业务浏览器验收仍待人工建号。
- 当前服务：Java 8080健康、PostgreSQL 55432、用户端5173、管理端5174均在线。

剩余不变：全面依赖/隐私/注入矩阵、管理员浏览器及交互建号、真实进程中断/多Worker、人工语义标注、其他旧数据授权、远端CI与生产目标、JPA计划偏差。未将这些项标记完成。

## 2026-09-20 检查点 4（历史）

运行时仍为Java + Spring AI，完整验收未完成，任务计数保持7 verified / 13 partial / 1 optional。

- 新增认证限流：连接IP、30次/60秒、最多10000来源、429/Retry-After，不信任X-Forwarded-For；窗口过期、容量和代理头绕过测试通过。它是单实例内存控制，代理共享额度，不能代替生产网关。
- Agent草稿强制先成功检索，候选人必须已召回且阅读证据；有召回结果时拒绝空草稿。真实HTTP/模型回归9项通过。
- 使用GitHub Advisory扫描工具检查33个关键后端依赖，初扫49条公告（6 critical、16 high、21 medium、6 low），升级Boot3.5.16、AI1.1.8及POM中的补丁版本后，同范围复扫0命中。报告见cve-java-b04.json及cve-java-b04-rescan.json。仅是依赖版本命中，不代表49条均可由应用利用；也不代表全量传递依赖/npm/镜像已扫完。
- 初试Tomcat10.1.58无法解析；中央仓库实际发布10.1.60，改用后验证通过。升级后完整真实套件22项，0失败、0错误、0跳过。测试只使用合成资料及随机schema。
- 新增backup.test.mjs：真实随机数据库应用V1-V5，1份合成文件恢复成功，文件篡改和数据库引用文件缺失均拒绝；finally仅清理自建数据库和临时目录。1项通过；不等于非空停写创建或生产切换恢复已验证。
- 本日数据库起初未启动，首轮测试因连接拒绝未执行业务；恢复原独立Compose容器后重跑通过，未使用模拟模型/数据库。
- 本轮未修改前端业务代码。此前两端构建证据保留；本轮浏览器仅检查启动/匿名路由，管理员及完整业务浏览器验收仍未完成。Vite缓存冲突已通过重建预构建缓存解除。

剩余：全面依赖/隐私/注入与预算矩阵、管理员浏览器及交互建号、真实进程中断/多Worker、人工语义标注、其他旧数据授权、远端CI与生产目标、JPA计划偏差。未将这些项标记完成。

## 2026-09-19 检查点 3（历史）

结论：运行时后端已是Java + Spring AI，两端Vue不再请求Django；旧代码为保留对照，不应删除旧数据来制造“迁移完成”。完整生产验收仍未完成。

### 本批新增

- SearchService支持工龄下限、学历、技能条件及关键词/向量召回，返回按简历合并的候选人，归属条件始终在服务端。
- V5为证据增加version/source。纠错先生成真实向量，再做版本检查和事务提交；外部调用失败不会产生半个新版本。新检索/引用只接受当前版本，历史原文保留，人工校对明确标为CORRECTION。旧V4中已纠错的记录需重新保存纠错以生成新证据，不能冒用旧原文；当前开发库无此类记录。
- 岗位DELETE与任务/报告级联，前端删除确认；修复刷新后未选中任务岗位的问题。增加安全的400/404/405/500响应，不暴露堆栈。
- 租约过期再次领取、唯一报告、真实模型澄清/答复/续跑回归通过；修复重试耗尽只改数据库却不通知SSE的问题。
- 清理两端全部已定位的console日志和8000硬编码调用；移除GPT-4/多模态等未经验证的页面承诺。
- 增加只读旧SQLite审计、真实PostgreSQL CI配置和停写配套备份/临时库恢复工具。

### 验证结果

- 最终 `node java-backend/dev.mjs test-real`：20 tests，0 failures，0 errors，0 skipped。AuthIntegrationTest现为9项，其他测试类数量与检查点2相同。
- 中间一次真实模型忽略“先澄清”请求导致失败；调整控制提示优先级后，定向8项及最终20项均通过。模型行为仍具非确定性，不声称任何提示都能强制安全语义。
- `node --test java-backend/tools/legacy-audit.test.mjs`：1项通过，合成SQLite字节前后不变，报告不包含账号或密码。
- 实际只读审计：旧SQLite的招聘者、管理员、简历、提取表均为0行，所有已检查风险计数为0；未读取密码字段、未写库、未调用模型。磁盘个人简历和旧SQL导出未导入。
- 两端最新构建通过，仍有大包/Browserslist警告；源码检查无console日志或8000调用。CI YAML解析通过，但当前没有Git仓库/远端执行证据，不冒称CI已在GitHub通过。
- 备份工具在后端在线时拒绝运行；停止本项目Java后创建快照，并恢复到随机临时库成功，最后删除临时库。快照含0份简历，非空文件恢复仍需补验；备份目录已忽略，不能上传。
- 最新服务已在8080启动，V4->V5升级通过；健康200、匿名me401。重启不是“执行中任务被杀后恢复”的完整演练。
- 本轮浏览器工具搜索返回不可用，未作新增删除按钮/来源标签/管理员页面浏览器验收；检查点2的浏览器结果仅覆盖当时版本。

### 剩余门槛

管理员须由用户在本机隐藏密码终端创建，再进行浏览器验收；不创建默认管理员、不提升已有用户。人工标签、其他旧数据来源、生产部署目标及远端CI权限需要明确。仍待安全依赖扫描/限流、完整提示注入与预算故障矩阵、多人语义效果评估、执行中断恢复及非空配套文件恢复。持久化为JdbcTemplate的架构偏差仍明确保留。详见计划，未验收项不勾选。

## 2026-09-19 检查点 2（历史）

Java + AI Agent 核心已运行。下方检查点1及初次服务探测是历史记录，不代表当前阻塞。完整迁移尚未验收，逐任务见 [计划](plan.md)，运行入口见 [Java说明](../java-backend/README.md)。

### 实现与架构

- Spring Boot3.5.12 / Spring AI1.1.4 / JDK21；独立PostgreSQL16.15、pgvector0.8.6已启动，Flyway V1-V4创建账号、简历/版本/向量证据、岗位/任务/报告、文件清理队列。
- 持久化采用JdbcTemplate/TransactionTemplate，而非初始计划的JPA实体/Repository分层。SQL当前位于服务类；这是明确的实现偏差，不标记为JPA迁移完成。
- 会话/CSRF认证、管理员用户管理、同步批量上传、原文分段、真实模型抽取与Embedding、统计、纠错版本、删除派生数据及文件清理重试已实现。
- 岗位要求需先确认。Agent真实选择searchResumes/readEvidence/saveDraft/askClarification；服务器绑定用户身份，逐字校验引用，手动执行白名单工具并记录步骤/模型/用量/耗时。
- 后台任务返回202，支持SSE、取消、澄清、租约；报告只能本人审批，重复审批不更新审批时间，岗位/简历改动使旧报告过期。无自动录用、拒绝、外部通知。
- 两端Vue切换为Java Cookie/CSRF；用户端新增岗位、任务、报告/引用、审批、纠错工作台，移动端证据改为纵向排布。统计展示未知分组并修正3-6年工龄取值。

### 可复核证据

`node java-backend/dev.mjs test-real`：BUILD SUCCESS，17 tests，0 failures，0 errors，0 skipped。

| 测试类 | 数量 | 验证范围 |
|---|---:|---|
| FoundationTest | 1 | BCrypt |
| SecurityBoundaryTest | 5 | 健康检查、匿名401、CSRF及Origin边界（MockMvc切片） |
| DatabaseMigrationTest | 1 | 真实数据库迁移/重跑、唯一约束 |
| AiIntegrationTest | 1 | Java真实Chat JSON与1024维Embedding |
| DocumentParserTest | 3 | TXT、生成的PDF/DOCX、空扫描PDF拒绝、日期区间合并 |
| AuthIntegrationTest | 6 | 真实HTTP会话/管理员/越权；真实AI上传检索与任务SSE/报告审批/纠错失效/删除；JD确认；清理失败重试；取消/澄清状态边界 |

AI相关测试显式启用，测试使用随机schema与临时文件，只清理自建资源。安全切片使用MockMvc，并未用它替代真实数据库和模型测试。实际故障分支未全部覆盖，不能把17项解释为完整行为覆盖率。

- 两端 `npm --prefix <frontend> run build` 均通过；最后图表修改后用户端再次构建通过。仍有大包和Browserslist过期警告，未作无关依赖升级。
- 四种图表未知分组及3-6年字段的5项直接Node断言通过。
- 浏览器合成账号流程：注册、TXT上传、JD解析/确认、202任务、SSE到报告、引用原文、审批、纠错后STALE、详情与五张图表、删除（cleanupPending=false）、退出后me返回401、受保护路由跳转、重新登录均通过。
- 桌面及390px移动端截图已检查，无水平溢出。浏览器验证为本次交互执行，尚未保存为可重复E2E脚本。
- 浏览器上传的合成简历及衍生任务/报告已删除；开发public schema仍留有明确命名的合成账号与岗位。旧Django、旧库、原始简历未改动。

### 运行入口

- Java：http://127.0.0.1:8080/api/health
- 用户：http://127.0.0.1:5173/agent；管理：http://127.0.0.1:5174/
- 数据库：独立Compose项目iras-java-agent，55432端口，具名卷iras-java-agent_iras-agent-postgres。
- Node入口 `dev.mjs` 已使用；不需要绕过PowerShell执行策略。管理员创建为本机隐藏密码交互命令，尚未人工验收；未创建默认管理员。

### 未完成与已知风险

1. 旧数据只读导入dry-run及密码重置迁移、JPA计划偏差处理、完整冻结边界规格未完成。
2. 混合检索尚无结构化筛选；人工纠错使报告失效，但检索仍基于原始文档证据，尚未整合纠正事实。
3. 原文引用存在性不等于语义充分性；多人对比、模型澄清全流程、提示注入/敏感属性专项和人工标注集未完整验收，无准确率或Recall@K结果。
4. 租约恢复代码已实现，真实进程中断/并发Worker恢复演练未做。取消/180秒预算为协作式检查，不能立即中断在途HTTP调用；SSE仅单实例广播。
5. 管理员浏览器完整流程、所有上传/预算/并发故障分支、通用异常状态码、限流、日志全面脱敏、上传崩溃孤儿文件对账需继续补齐。旧前端仍含历史文案和调试输出。
6. CI、依赖安全扫描、配套数据库/原文件备份恢复和生产HTTPS交付未完成。Java编辑器未导入Maven项目产生的诊断尚未处理；可复现编译依据是Maven实际成功结果。

下方为历史记录；旧阻塞已解除，不再要求修改已验证的模型配置或数据库网络。

## 2026-09-17 检查点 1

- 已保存 ../MIGRATION_BASELINE.md，脚本验证 REQ-001 至 REQ-021 全部存在。
- ../java-backend/pom.xml 使用 Java 21 / Spring Boot 3.5.12 / Spring AI 1.1.4。与讨论时的 Boot 4 / AI 2 方案不同，当前只验证此同代组合的编译；没有声称已实现 AI 2 的功能。
- Maven 3.9.16 已安装；3.9.11 下载地址返回 404 后改用工具的 latest。
- `mvn -f java-backend/pom.xml test -B -ntp`：BUILD SUCCESS，6 tests，0 failures，0 errors，0 skipped。
- FoundationTest 1 项、SecurityBoundaryTest 5 项。边界测试使用 MockMvc，不代替用户指定的真实服务验收。
- Compose 修正重复声明后通过 `docker compose -f java-backend/compose.yaml config --no-interpolate --quiet`。
- 旧 Django、Vue、数据库、原始简历未修改；Java 尚未启动到真实数据库。

## 历史状态（检查点1）

T001 部分 implemented：编译、MVC 安全边界已验证；完整服务启动/统一业务错误处理未完成，暂不勾选。
T021 部分 implemented：Compose、环境模板、启动脚本与说明已建立；真实启动、CI、备份恢复未验收。
其他必需任务 pending；T017 optional。业务迁移与 Agent 功能均未完成，不将骨架视为完成。

## 历史环境阻塞（已解除）

用户已选择真实服务，并同意独立 Docker PostgreSQL；模型由用户在本机配置。
2026-09-19 已确认全部必填项存在、无重复键或无效配置行，Docker 引擎 29.0.1 可用。此前未配置和引擎未启动的阻塞已解除。
当前阻塞是 Docker Hub 镜像拉取网络失败；数据库未启动，不能验证数据库密码、Flyway 或业务持久化。具备连接条件后先执行 T002/T003，再按顺序继续。

## 2026-09-19 初次真实服务检查（历史）

| 检查项 | 结果 | 验证范围 |
|---|---|---|
| 必填配置 | PASS | 数据库、Chat、Embedding 共 9 项存在；不输出值；未修改用户配置 |
| Docker 引擎 | PASS | 服务端版本 29.0.1 |
| 数据库目标 | PASS | 匹配已授权的本机 55432 端口和 iras_agent 数据库 |
| Chat JSON | PASS | 合成提示返回可解析 JSON，ok 为 true |
| 强制指定工具 | HTTP 400 | 本次具名 tool_choice 请求未通过；不据此判断所有工具调用均不支持 |
| 自动工具选择 | PASS | tool_choice=auto，模型选择 connectivity_probe 且参数为 synthetic |
| 工具结果闭环 | PASS | 本地返回合成工具结果后，模型正常结束工具调用并回复预期标识 |
| Embedding | PASS | 合成文本返回 1024 个有限数值；后续向量 schema 应采用实测维度，不默认 1536 |
| 数据库镜像 | BLOCKED | pgvector/pgvector:pg16 拉取时 registry-1.docker.io:443 连接超时；Docker 报告未配置 HTTPS proxy；本地无 PostgreSQL/pgvector 缓存镜像 |
| 数据库 TCP | BLOCKED | 目标端口不可连接；密码认证尚未验证 |

模型检查通过本机 Node fetch 调用真实 HTTP 服务，仅发送合成资料；尚未验证 Spring AI Java 客户端的同等调用，更不表示项目 Agent 已实现。默认采用 auto 工具选择；接口层仍须校验工具名称、参数、归属及调用预算。

已尝试 `docker compose --env-file java-backend/.env.real -f java-backend/compose.yaml up -d --wait database`，在镜像下载阶段失败。未改动 Docker 全局代理、DNS、镜像源或其他项目容器。需要用户确认 Docker Desktop 代理/DNS/网络可访问 Docker Hub，或提供可信离线镜像；不能擅自改用 MySQL 或 Mock 替代真实 PostgreSQL 验收。

修复网络后可在仓库根目录执行 `docker pull pgvector/pgvector:pg16`，再执行上述 Compose 命令。这两条命令无需运行 PowerShell 脚本，也不把密钥放入命令行。

## 当时尚未验证（以检查点2为准）

本机 PowerShell 执行策略拒绝运行 java-backend/run.ps1；没有绕过策略。脚本编辑器诊断无错误，但运行行为未验收。唯一启动入口为 run.ps1。

真实数据库读写与迁移、Java 客户端 Chat/Embedding 与工具编排、完整 HTTP 服务启动、浏览器端到端、两端新构建、依赖安全与效果评测。冻结式接口测试包尚未形成，当前行为基准是功能与约束清单，不冒称为完整冻结测试规格。