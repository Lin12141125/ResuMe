# 运行验证报告

## 环境

- Windows，隔离 .venv 使用 Python 3.13；终端默认 Python 3.14 不作为本次验证环境。
- 实际 Django 5.2.17，Node.js 24.11.1，npm 11.6.2。
- Docker CLI 存在，但 `docker info` 无法连接 dockerDesktopLinuxEngine，故数据库验证使用 SQLite，不声称 MySQL 行为已验证。
- 浏览器自动化使用 VS Code 集成 Playwright。真实 API 密钥和简历数据未使用。

## 检查结果

| 检查 | 结果 | 证据与范围 |
| --- | --- | --- |
| Django 启动 | PASS | 本地 127.0.0.1:8000，检查无错误，local 规则模式 |
| migrate | PASS，退出码0 | SQLite 创建业务表和 Django 系统表 |
| makemigrations --check --dry-run | PASS，退出码0 | No changes detected |
| manage.py test IRAS | PASS，退出码0 | 12 passed，0 failed，隔离测试库和临时文件目录 |
| test_fixes.py | PASS，退出码0 | 同一12个测试，已移除真实数据删除行为 |
| 用户端构建 | PASS，退出码0 | Vite production build；存在大包/Browserslist提示 |
| 管理端构建 | PASS，退出码0 | Vite production build；存在大包/Browserslist提示 |
| 用户端浏览器 | PARTIAL | 未登录跳转、注册、文本上传、列表、详情、5个图表均成功；删除点击受自动化工具超时影响，未完成浏览器验收 |
| 管理端浏览器 | PARTIAL | 发现并修复入口白屏；登录与用户列表成功，没有密码列；完整管理交互未验收 |
| 手机视口 | PASS（局部） | 390x844 上传、详情页面级横向溢出修复；非所有页面响应式验收 |
| 真实 LLM | UNVERIFIED | 仅 SDK mock 契约测试；无用户提供的有效密钥，不调用外部服务 |
| MySQL/旧数据 | UNVERIFIED | 未连接原库；历史 SQL 表结构不一致，需单独迁移 |

## 后端测试覆盖

1. 注册密码哈希、上传、原文/JSON持久化、查看、统计、删除清理。
2. 空数据和未解析记录统计。
3. 空文件、空批次、缺少 AI 密钥拒绝。
4. 模型无效返回和磁盘写入失败时不残留数据库记录/文件。
5. 匿名、普通用户访问管理员接口、跨用户查询/删除拒绝。
6. 正确/错误登录、错误方法与损坏 JSON。
7. 旧管理员明文密码登录后升级为哈希、列表不返回密码、删除用户。
8. 重叠工龄合并、模型字段类型归一化。
9. 真正的合成 PDF 字节提取邮箱、无效 PDF 拒绝。
10. OpenAI SDK JSON输出参数及 Markdown fenced JSON 处理。
11. 选择简历统计与越权列表拒绝。
12. 混合有效/不支持文件批次不产生部分数据。

测试中的 `OSError: disk unavailable` 是主动注入的失败路径，最终套件 OK，并非实际磁盘故障。SQLite 的4条 db_comment 警告只涉及列注释。

## 结论

本地规则模式核心后端与主要界面可运行。生产安全、真实大模型效果、旧数据库兼容、全部浏览器交互仍需专项验收，不使用“所有问题已解决”的结论。