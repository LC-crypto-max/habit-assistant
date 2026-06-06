# 小规模公网部署方案

适用场景：2-3 个可信用户通过公网访问 Habit Assistant，后端保存授权上传的行为数据、画像和推荐。本地 worker 仍在用户自己的电脑运行，公网服务器只接收回调和展示结果。

## 1. 打包

```powershell
mvn -DskipTests package
```

产物：

```text
target/habit-assistant-java-0.0.1-SNAPSHOT.jar
```

## 2. 运行 Jar

建议使用 MySQL profile，并开启轻量登录：

```powershell
$env:HABIT_AUTH_ENABLED="true"
$env:HABIT_ALICE_PASSWORD="请改成强密码"
$env:HABIT_BOB_PASSWORD="请改成强密码"
$env:HABIT_ADMIN_PASSWORD="请改成强密码"

java -jar target/habit-assistant-java-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=mysql `
  --assistant.auth.enabled=true `
  --assistant.local-worker.enabled=false
```

注意：公网服务器不要开启 `assistant.local-worker.enabled=true`。本地 worker 应由用户在自己的电脑运行。

## 3. MySQL

准备数据库：

```sql
CREATE DATABASE habit_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'habit_user'@'%' IDENTIFIED BY 'change_me';
GRANT ALL PRIVILEGES ON habit_assistant.* TO 'habit_user'@'%';
FLUSH PRIVILEGES;
```

运行时配置连接信息，推荐用环境变量或独立 `application-mysql.yml`，不要把真实密码提交到 GitHub。

## 4. Nginx 反向代理

示例：

```nginx
server {
    listen 80;
    server_name your-domain.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

生产建议配置 HTTPS：

```bash
certbot --nginx -d your-domain.example.com
```

## 5. 创建 2-3 个用户

第一版使用配置用户：

```yaml
assistant:
  auth:
    enabled: true
    users:
      - user-id: alice
        password: ${HABIT_ALICE_PASSWORD}
        admin: false
      - user-id: bob
        password: ${HABIT_BOB_PASSWORD}
        admin: false
      - user-id: admin
        password: ${HABIT_ADMIN_PASSWORD}
        admin: true
```

公网部署时必须修改默认密码。

## 6. 保护管理接口

`POST /api/admin/dev/reset-data` 会备份并清理测试数据。公网部署建议：

- 不暴露该接口给公网；
- Nginx 层限制 `/api/admin/` 只允许管理员 IP；
- Spring Boot 开启 `assistant.auth.enabled=true`；
- 仅管理员账号使用；
- 操作前确认备份路径。

Nginx 限制示例：

```nginx
location /api/admin/ {
    allow 你的固定公网IP;
    deny all;
    proxy_pass http://127.0.0.1:8080;
}
```

## 7. 查看日志

后台运行建议把输出重定向：

```powershell
java -jar target/habit-assistant-java-0.0.1-SNAPSHOT.jar *> logs/prod-app.log
```

Linux 可用 systemd 管理，日志通过 `journalctl -u habit-assistant -f` 查看。

## 8. 备份数据

MySQL：

```bash
mysqldump -u habit_user -p habit_assistant > backup_$(date +%Y%m%d_%H%M%S).sql
```

本地文件：

```powershell
Compress-Archive -Path data -DestinationPath backups\data_$(Get-Date -Format yyyyMMdd_HHmmss).zip
```

## 9. 清理测试数据

开发环境可调用：

```powershell
Invoke-RestMethod -Method Post "http://localhost:8080/api/admin/dev/reset-data" |
  ConvertTo-Json -Depth 6
```

接口会先备份到：

```text
data/backups/YYYYMMDD_HHmmss/
```

然后清空行为、画像、推荐、Agent Query 和 Codex Agent 相关 JPA 数据。表结构不会删除。

## 10. 本地 Worker 注意事项

- 不要把本地 worker 部署到公网服务器。
- 用户在自己的电脑运行 `scripts/codex_query_worker.py`。
- worker 只回传授权范围内的非敏感摘要。
- 不读取 Cookie、Token、Session、账号密码、聊天记录、私信、支付记录。
- 如果启用 `--use-codex-cli`，Codex CLI 只分析 worker 已采集的 `raw_items`，不自行读取系统文件或联网抓取。
