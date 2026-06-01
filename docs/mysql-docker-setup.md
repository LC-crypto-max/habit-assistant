# Docker MySQL 与 Nginx 本地协作环境

## 为什么使用 3307

很多 Windows 电脑本机已经安装了 MySQL，占用了宿主机 `3306`。如果 Docker 继续使用 `3306:3306`，会出现：

```text
ports are not available: exposing port TCP 0.0.0.0:3306
bind: Only one usage of each socket address is normally permitted.
```

因此本项目 Docker MySQL 使用：

```text
宿主机 3307 -> 容器 3306
```

Spring Boot `dev` profile 连接 `localhost:3307`。

## 清理失败容器

如果之前创建过失败的 `habit-mysql` 容器，先删除：

```powershell
docker rm habit-mysql
```

## 启动 MySQL

首次运行前复制环境变量模板，并把 `.env` 中的密码改成你自己的本地密码：

```powershell
Copy-Item .env.example .env
notepad .env
```

```powershell
docker compose up -d mysql
```

查看状态：

```powershell
docker ps
```

查看日志：

```powershell
docker logs habit-mysql
```

验证 `habit` 用户：

```powershell
docker exec -it habit-mysql mysql -u$env:MYSQL_USER -p$env:MYSQL_PASSWORD -e "SHOW DATABASES;"
```

## 启动 Spring Boot

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

或：

```powershell
java -jar target\habit-assistant-java-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## 启动 Nginx

```powershell
docker compose up -d nginx
```

访问提交页：

```text
http://localhost:8088
```

访问完整画像和推荐后台：

```text
http://localhost:8088/assistant/
```

`/api/` 会被 Nginx 代理到宿主机 Spring Boot：

```text
http://host.docker.internal:8080
```

如果后端以后也容器化，可以把 `nginx/nginx.conf` 中的 upstream 改为服务名，例如：

```text
http://app:8080
```

## 常见故障

### Docker daemon 无法连接

先启动 Docker Desktop，再执行：

```powershell
docker ps
```

### habit-mysql 是 Created 但没有 Up

查看日志：

```powershell
docker logs habit-mysql
```

如果是端口冲突，确认没有继续使用 `3306:3306`。

### Access denied for user 'habit'@'localhost'

通常是 Spring Boot 连到了本机 MySQL，而不是 Docker MySQL。确认：

```text
spring.datasource.url=jdbc:mysql://localhost:3307/habit_assistant
```

### Nginx 页面能打开但提交失败

确认 Spring Boot 正在宿主机 `8080` 运行：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```
