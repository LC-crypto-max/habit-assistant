# 修改报告

## 背景

本轮按照项目内安装的 skill 做了代码审查、API 检查、测试补强和文档整理。使用到的本地 skill：

- `code-reviewer`：按安全、性能、正确性、可维护性顺序审查。
- `api-design-assistant`：检查 REST API 命名、请求响应和错误格式。
- `api-documentation-generator`：补充 API 文档。

## 本轮改动

### 1. 推荐逻辑增强

处理：

- 新增 `POST /api/recommendations/search`。
- 搜索关键词会先沉淀为用户搜索行为，再触发推荐生成。
- 推荐排序从单纯兴趣词命中升级为：标题/摘要/标签命中、本次搜索词、新鲜度、历史反馈共同打分。
- `LIKE`、`FAVORITE`、`READ` 会提升相似平台和标签；`DISLIKE` 会降低相似内容；`BLOCK_SOURCE` 会过滤同平台候选。
- 用户提交推荐反馈后，会额外写入一条行为记录，让画像继续学习。

### 2. 本地采集补充

处理：

- 新增 `scripts/import-browser-history.ps1`：导入 Chrome/Edge 最近浏览历史。
- 新增 `scripts/collect-app-usage.ps1`：检测指定本机应用进程，例如哔哩哔哩客户端，并上报为行为。
- 采集脚本均为用户显式触发，不做静默后台读取。

### 3. 前端体验

处理：

- 重写 `src/main/resources/static/index.html`。
- 页面打开后自动调用 `/api/recommendations/today`，如果推荐过期由后端自动刷新。
- 增加关键词搜索推荐、推荐刷新状态、画像关键词、推荐反馈按钮。
- 所有动态 HTML 输出继续走 `escapeHtml` 和 `safeUrl`，降低 XSS 风险。

### 4. 中文乱码修复

处理：

- 修复配置、Mock 内容源、飞书返回文案、异常返回文案、访问记录 CSV 中文表头识别、测试样例中的乱码。
- `application.yml` 与测试配置统一保持 UTF-8。

### 5. 验证脚本

处理：

- `scripts/verify-local.ps1` 保持后台启动、日志写入、finally 停止进程。
- 由于本机 PowerShell/Java 25 组合在脚本启动验证时出现过超时或进程管道问题，本轮最终以 MockMvc 作为主要代码级验证方式。
- README 已补充 `-StartupTimeoutSeconds 45` 的手动放宽方式。

## 审查结果

### 安全

- 前端动态渲染已做 HTML 转义。
- 推荐链接通过 `safeUrl` 限制为 `http/https`。
- 数据访问使用 Spring Data JPA Repository，没有发现用户输入拼接原生 SQL。
- 风险仍然存在：`/api/admin/migration/**` 当前未加管理员鉴权，不建议直接暴露到公网。

### 性能

- 推荐生成会读取最近 50 条反馈作为轻量排序信号，适合当前 5~10 用户规模。
- 后续内容量扩大后，建议把候选召回迁移到 Elasticsearch/OpenSearch，并为列表接口补分页。

### 正确性

- 推荐搜索、反馈、历史导入、迁移导入导出均有 MockMvc 覆盖。
- 浏览器历史脚本依赖本机 `sqlite3`，缺失时会给出明确错误。

## 测试结果

已执行：

```powershell
mvn -DskipTests compile
mvn test
```

结果：

- 编译通过。
- MockMvc 测试通过。
- 当前测试数：6。

覆盖范围：

- 健康检查。
- 习惯提交成功、缺字段、内容过长、日期格式错误。
- 微信小程序接口。
- 关键词搜索推荐、推荐反馈、刷新策略、强制刷新、飞书无 webhook 分支。
- 本机应用行为上传。
- 行为迁移和完整迁移导出。
- 历史导入手动触发。
- 通用参数校验错误结构。

## 环境说明

Windows 下曾出现 `target/classes/static/index.html` 拒绝访问。处理方式是停止残留 Java 进程后仅删除该 target 编译产物，再重新编译，没有执行大范围清理。

本轮没有继续使用前台 `java -jar`。后台脚本验证在当前 PowerShell 环境曾被中断或超时，因此使用 `mvn test` 作为可靠验证方式。

## 后续建议

1. 增加登录鉴权，避免长期依赖前端传入 `userId`。
2. 给 `/api/admin/migration/**` 增加管理员保护。
3. 为列表类接口增加分页，避免数据量增长后一次性返回过多。
4. 生产环境启用 HTTPS、限流和访问日志审计。
5. 如需更智能推荐，可引入向量召回或大模型重排，但要先补齐用户授权和数据脱敏策略。

## 2026-06-01 前端与 GitHub 准备

本轮处理：

- 优化 `src/main/resources/static/index.html`，统一为现代、简洁、扁平化的后台界面。
- 优化 `web/index.html`，作为 Nginx 协作提交页，补充 loading、禁用按钮和友好错误提示。
- 新增 `.gitignore`，忽略 `target/`、`logs/`、`data/`、`.idea/`、`.env`、本地数据库文件和前端构建产物。
- 新增 `.env.example`，把 MySQL、飞书和 X 的敏感配置改为用户自行填写。
- 修改 `docker-compose.yml`、`docker-compose.mysql.yml`、`application-dev.yml`、`application-mysql.yml`，不再提交明文默认数据库密码。
- 重写 README，使其更适合 GitHub 展示和面试介绍。

验证：

- `mvn test` 通过。
- 使用临时本地静态服务和 mock API 验证前端页面可渲染推荐、画像关键词和刷新状态。
- `docker compose config` 在临时环境变量下解析通过。

已知环境问题：

- 当前目录不是 Git 仓库。
- 执行 `git init` 时，Windows 给半初始化 `.git` 目录继承了 Deny ACL，导致 `.git/config` 无法写入。
- 当前需要用户手动删除半初始化 `.git` 目录或修复 ACL 后再执行 `git init`。
