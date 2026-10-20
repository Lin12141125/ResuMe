# Java Agent 迁移执行计划

规范：../MIGRATION_BASELINE.md 是 REQ-001 至 REQ-021 的行为要求。本文记录任务状态，不替代行为基准。

## 架构与约束

- 目标 java-backend 为 Java 21 / Spring Boot / Spring AI 的模块化单体；PostgreSQL + pgvector 为新库，Flyway 独立建表，不连旧 SQLite/MySQL。
- 旧 Vue 端接口兼容；认证使用 HttpOnly 服务端 Session 和 CSRF Token，前端必须一起修改。旧 token 不能复用。
- Controller 负责协议，Service 负责所有权及状态规则，Repository 负责查询；Agent 工具只能调用已鉴权的业务服务。
- 简历原文/解析版本/证据片段分开保存；检索与报告引用同时限制 owner 和 resume version。模型输出不是可信授权依据。
- 工具和 LLM 调用不持有数据库长事务；数据库是任务进度与审批事实来源。只有安全读步骤自动恢复，非幂等外部操作不得自动重放。
- 数据库/聊天/Embedding 使用真实服务验证。测试合成文件，禁止真实个人资料；未提供服务即标 blocked，不能假造通过。
- 遵循现有前端设计，无营销页；不安装无关基础设施，不提交 git，不删除旧实现。

## 接口设计

保留 /IRAS/ 的 UserRegister、UserLogin、AdminLogin、ResumeUpload2Interpret、FindUserResumes、SingleResumePresent、ResumeStatistic、ResumeDelete、FindAllUsers、UserDelete；保持原请求与数据字段，登录 token 字段只作为前端过渡标识，真正认证依赖 Cookie，不作为 Bearer 凭证。

新增 API：GET /api/auth/csrf、GET /api/auth/me、POST /api/auth/logout；GET/POST /api/jobs、PUT /api/jobs/{id}、POST /api/jobs/{id}/confirm；GET /api/resumes/{id}/evidence、PUT /api/resumes/{id}/extraction；POST /api/agent/tasks、GET /api/agent/tasks/{id}、POST /api/agent/tasks/{id}/reply、POST /api/agent/tasks/{id}/cancel、GET /api/agent/tasks/{id}/events；GET /api/reports、GET /api/reports/{id}、POST /api/reports/{id}/approve。接口落地后将实际契约写入接口文档；不实现的接口必须标记 pending，不能占位返回伪造成功。

## 分阶段任务

- [x] T001 [Plan:1] Java 可构建启动、鉴权默认拒绝。[REQ-001] java-backend/pom.xml
- [ ] T002 [Plan:1] 独立数据库、版本迁移、实体与数据导入 dry-run。[REQ-002] java-backend/src/main/resources/db/migration/ [Source: mysite/IRAS/models.py]
- [x] T003 [Plan:1] 用户/管理员认证、会话、CSRF、归属校验。[REQ-003] java-backend/src/main/java/ [Source: mysite/IRAS/views.py#api,user_register,user_login,admin_login]
- [ ] T004 [Plan:1] 简历上传/列表/详情/删除/统计。[REQ-004] java-backend/src/main/java/ [Source: mysite/IRAS/views.py#resume_upload2interpret,find_user_resumes,single_resume_present,resume_delete,resume_statistic]
- [ ] T005 [Plan:1] 两端会话接口、岗位/Agent 页面、登录退出。[REQ-005] vue-ending-project/src/ vue-admin/src/
- [x] T006 [Plan:2] PDF/TXT/DOCX 文本及证据片段。[REQ-006] java-backend/src/main/java/
- [ ] T007 [Plan:2] 真实结构化抽取、日期规则、纠错版本。[REQ-007] java-backend/src/main/java/ [Source: mysite/IRAS/utils/resumeExtration.py#parse_resume,calculate_work_years]
- [x] T008 [Plan:2] 岗位 CRUD、JD 解析、确认标准。[REQ-008] java-backend/src/main/java/
- [x] T009 [Plan:2] 用户隔离混合检索、候选人去重。[REQ-009] java-backend/src/main/java/
- [ ] T010 [Plan:2] 可解释匹配、证据引用验证。[REQ-010] java-backend/src/main/java/
- [ ] T011 [Plan:2] 对比与面试问题。[REQ-011] java-backend/src/main/java/
- [x] T012 [Plan:3] 会话澄清与任务上下文。[REQ-012] java-backend/src/main/java/
- [x] T013 [Plan:3] 实际 Tool Calling、工具权限。[REQ-013] java-backend/src/main/java/
- [ ] T014 [Plan:3] 多轮补查、轮次/时间/调用预算。[REQ-014] java-backend/src/main/java/
- [ ] T015 [Plan:3] 人工审批、纠错后报告失效、幂等。[REQ-015] java-backend/src/main/java/
- [ ] T016 [Plan:3] 异步任务、取消、恢复、SSE。[REQ-016] java-backend/src/main/java/
- [ ] T017 [Plan:optional] 外部系统联动（可选，未配置禁用）。[REQ-017]
- [ ] T018 [Plan:4] 权限、注入、隐私清理验证。[REQ-018] java-backend/src/test/
- [ ] T019 [Plan:4] 运行观测、预算、调用事件。[REQ-019] java-backend/src/main/java/
- [ ] T020 [Plan:4] 真实服务回归、浏览器与效果评估。[REQ-020] java-backend/src/test/
- [ ] T021 [Plan:4] Compose、CI、环境和备份说明。[REQ-021] java-backend/compose.yaml

## Requirement Mapping

REQ-001..REQ-021 与 T001..T021 一一对应。实现证据是任务所列路径；每项只有关联检查通过后才能勾选。可选任务和环境阻塞不得计为完成。

## 当前环境与检查点

2026-09-20检查点5：新增AgentRunnerBudgetTest（轮次/工具预算耗尽、未授权工具拒绝）与无模型上传失败矩阵测试，非AI套件26项通过、4项AI跳过；修复Vite依赖缓存损坏并恢复两端前端。任务勾选与7/13/1计数不变，详见执行记录检查点5。

2026-09-20检查点4覆盖下方检查点3的测试和安全状态：Boot3.5.16/AI1.1.8，完整真实套件22项通过、无跳过；认证单实例限流和Agent检索/阅读前置校验已验证。33个关键后端依赖初扫49条公告，同范围补丁后复扫0。真实临时库含1份合成文件恢复、损坏/缺失拒绝测试通过。全量依赖、生产网关、非空停写创建及正式恢复仍未验收；任务勾选和7/13/1计数不变。详细范围见执行记录检查点4；下方为检查点3历史明细。

2026-09-19检查点3：独立 PostgreSQL16/pgvector 已运行，V1-V5 可在随机schema迁移和重跑；真实Spring AI Chat、1024维Embedding、自动工具链均通过。Java监听8080，两端Vue为5173/5174；前端已无8000调用或敏感console输出，旧Django只保留对照。

持久化当前使用JdbcTemplate和TransactionTemplate显式SQL，不是JPA实体实现。未改变既定权限和验收条件；T002不因新表存在就视为完成旧数据迁移。

最新 `node java-backend/dev.mjs test-real`：20 tests，0 failures，0 errors，0 skipped；只读SQLite审计1项Node测试通过。两端最新构建通过。检查点2已验证用户浏览器核心链路；检查点3浏览器工具不可用，新按钮与来源标签未作浏览器复验，管理员完整浏览器流程仍未验收。

| 任务 | 当前状态 | 剩余或边界 |
|---|---|---|
| T001/T003/T006/T013 | verified | 已勾选；生产级认证加固不在此处冒称完成 |
| T002 | implemented（部分） | 五次迁移及只读dry-run通过；旧SQLite四张业务表均为0行，无记录需导入；其他数据源与JPA偏差待确认 |
| T004/T007 | implemented | 主链路与纠错验证通过；B05新增无模型上传失败矩阵（空批/超量/类型/空文件/超长/越权/匿名）；真实AI故障注入与并发仍待覆盖 |
| T005 | implemented | 用户浏览器通过；管理员端仅构建和HTTP业务验证 |
| T008 | verified | JD解析、编辑、确认、归属删除及任务/报告级联通过HTTP测试 |
| T009 | verified | 工龄/学历/技能过滤、候选人分组、用户隔离及纠错版本检索通过真实服务测试 |
| T010/T011 | implemented | 原文引用校验、候选人维度对照和面试问题；人工标注语义质量与多人场景仍待验收 |
| T012 | verified | 真实模型askClarification、WAITING、回复上下文、续跑完成已验证；提示控制不等于任意输入的形式化保证 |
| T014 | implemented | 轮次/工具/时限控制；B05新增轮次与工具预算耗尽、未授权工具确定性单元测试；时间预算耗尽与注入专项仍待覆盖 |
| T015 | implemented | 本人审批、幂等、纠错过期已验证；岗位并发变更场景待补 |
| T016 | implemented | 过期租约恢复、唯一报告、耗尽失败SSE通知通过；真实进程中断中的任务及多Worker演练待做 |
| T017 | optional | 无外部联动工具，未发送任何外部操作 |
| T018 | implemented（部分） | CSRF/越权/引用/删除清理、前端敏感日志清理；限流、全面隐私与依赖审查未完成 |
| T019 | implemented（部分） | 持久化工具步骤、模型、耗时、供应商返回用量；无虚构费用，无完整指标看板 |
| T020 | implemented（部分） | 20项真实服务套件、只读审计测试、前一批用户浏览器验收；非完整冻结规格或人工效果基准 |
| T021 | implemented（部分） | CI配置已创建并解析；本地停写备份及临时库恢复通过（0份简历）；远端CI、非空文件恢复与生产交付待做 |

下一批需明确管理员账号与浏览器环境、是否有SQLite之外的旧数据、人工标注集及部署目标；随后完成多人/注入/预算故障矩阵、安全扫描和生产验收。当前7项verified、13项implemented、1项optional，不是100%迁移完成。