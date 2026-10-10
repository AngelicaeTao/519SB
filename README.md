# 519SB 聊天室

基于 Java 21 原生 HTTPS + SSE 的简易聊天室，支持账号系统、个人中心、图片消息、撤回、@提醒、在线用户、管理员控制台。外网访问统一通过 ngrok 转发。

## 三分钟快速上手

1. 装 JDK 21+（https://adoptium.net/）
2. 双击 `setup.bat` 生成证书
3. 用 IDEA 打开项目，运行 `BootstrapLayer.ChatServer`
4. 另开命令行运行 `ngrok http https://localhost:8443 --region ap`
5. 把 ngrok 给的 `https://xxx.ngrok-free.app` 发给朋友

**不用改任何配置，默认就能跑。**

---

## 功能特性

### 基础
- 门禁验证码（首次访问需输入，通过后 30 天免输）
- 账号注册、登录（密码要求：6~50 位，必须含字母和数字）
- 登录频率限制（默认 5 次 / 分钟，防暴力破解）
- 消息频率限制（默认 5 条 / 3 秒，防刷屏）
- 密码 PBKDF2 + 随机盐存储，不存明文

### 聊天
- 文字消息 SSE 实时推送
- 图片发送（前端自动压缩到最大 800px，JPEG 质量 0.6）
- 图片点击放大预览，支持下载
- 头像显示（每条消息带头像、昵称、时间戳）
- **消息撤回**（自己发的消息 2 分钟内可撤回，双方同步删除）
- **@提醒**（输入 `@` 弹出在线用户列表，被 @ 的人收到高亮 + 提示音）
- **未读计数**（切到后台时有新消息，标签标题显示 `(3) 519SB-Chat`）
- **消息分页**（滚到顶部自动加载更早的消息，每次 30 条）
- **在线用户列表**（右侧实时显示当前在线的人）
- 支持 Shift + Enter 换行

### 个人中心
- 修改昵称（与账号分离）
- 上传/更换头像（永久保留，不会自动清理）
- 修改密码（**必须验证旧密码**）

### 管理员控制台
- 查看所有用户（账号、昵称、身份、注册时间）
- 编辑用户（昵称、密码、头像、管理员权限）
- 删除用户（**在线用户强制踢出**，同时删除其所有消息）
- 查看指定用户的所有消息
- 单条 / 批量删除消息
- **清空所有聊天消息**（保留用户和头像）

### 安全
- HTTPS 加密（自签名证书）
- 密码 PBKDF2WithHmacSHA256 + 随机盐（迭代 10000 次）
- Token 会话 24 小时过期
- 所有登录相关接口走门禁校验
- 头像 ID 后端格式校验，防 XSS
- 前端全部使用 DOM API，无 `innerHTML` 拼接用户内容
- 消息列表使用虚拟线程处理，SSE 长连接不阻塞

---

## 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 21 或更高 | 必须 ≥ 21，代码用了虚拟线程 |
| Maven | 3.8+ | IDEA 自带 |
| ngrok | 最新版 | 外网访问必需 |

检查 JDK：

```bash
java -version
javac -version
keytool -help
```

---

## 项目结构

```
519SB/
├── config.properties.example    配置模板
├── setup.bat                    Windows 一键生成证书
├── pom.xml                      Maven 配置
├── README.md
├── .gitignore
└── src/main/java/
    ├── BootstrapLayer/
    │   └── ChatServer.java          程序入口
    ├── ConfigurationLayer/
    │   └── Config.java              读取配置
    ├── DataLayer/
    │   ├── User.java                用户模型
    │   ├── UserStore.java           用户数据访问
    │   └── MessageStore.java        消息数据访问
    ├── HandlerLayer/
    │   ├── AuthHandler.java         门禁/登录/注册
    │   ├── ChatHandler.java         聊天/SSE/撤回/在线/分页
    │   ├── ImageHandler.java        图片上传下载
    │   ├── ProfileHandler.java      个人中心
    │   └── AdminHandler.java        管理员接口
    ├── InfrastructureLayer/
    │   ├── DatabaseManager.java     SQLite 连接
    │   ├── HttpUtil.java            HTTP/JSON 工具
    │   ├── RateLimiter.java         频率限制
    │   ├── SessionManager.java      会话管理
    │   └── SseManager.java          SSE 连接池
    └── ViewLayer/
        ├── GatePage.java            验证码页
        ├── LoginPage.java           登录页
        ├── ProfilePage.java         个人中心页
        ├── AdminPage.java           管理员页
        └── WebPages.java            聊天页
```

---

## 配置说明

如果不想用默认配置，把 `config.properties.example` 复制为 `config.properties`，然后修改。

**保存时务必选择 UTF-8 编码**。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `port` | 8443 | 服务器端口 |
| `site.title` | 519SB-Chat | 网站标题 |
| `keystore.path` | keystore.p12 | 证书路径 |
| `keystore.password` | changeit | 证书密码 |
| `db.file` | data/chat.db | SQLite 数据库路径 |
| `max.history` | 200 | 历史消息保留条数 |
| `max.image.size` | 5242880 | 单张图片最大字节数（5MB） |
| `image.max.width` | 800 | 前端压缩图片最大宽度 |
| `image.quality` | 0.6 | 前端压缩 JPEG 质量 |
| `thread.pool.size` | 16 | HTTP 线程池大小（已改用虚拟线程，此值仅供参考） |
| `access.code` | 519666233 | 门禁验证码 |
| `login.max.attempts` | 5 | 登录最多尝试次数 |
| `login.window.ms` | 60000 | 登录限制时间窗口（毫秒） |
| `send.max.count` | 5 | 消息最多发送条数 |
| `send.window.ms` | 3000 | 消息限制时间窗口（毫秒） |
| `admin.username` | admin | 默认管理员账号 |
| `admin.password` | admin123 | 默认管理员密码 |

---

## 默认账号与验证码

| 类型 | 默认值 |
|---|---|
| 验证码 | `519666233` |
| 管理员账号 | `admin` / `admin123` |

### 上线前必做

1. **改管理员密码**：用 `admin` 登录 → 右上角头像 → 个人中心 → 修改密码
2. **改验证码**：修改 `config.properties` 的 `access.code`，重启生效

---

## 更新日志

### v1.2-fix（本次）
- **安全**：删除不需要登录的 `/api/changePassword`，改密码必须登录 + 旧密码校验
- **安全**：密码从纯 SHA-256 升级为 PBKDF2WithHmacSHA256 + 随机盐
- **安全**：`avatarId` 后端校验格式，防止存储型 XSS
- **安全**：登录、注册接口加门禁校验，防止绕过验证码
- **安全**：Session 加 24 小时过期，登录尝试频率限制
- **性能**：改用虚拟线程（`Executors.newVirtualThreadPerTaskExecutor`）
- **性能**：SQLite 启用 WAL 模式，SSE 广播异步化
- **功能**：消息撤回（2 分钟内）、@提醒、未读数标题
- **功能**：消息分页加载、在线用户列表
- **功能**：管理员可清空所有消息（保留用户）
- **修复**：头像不再被 24 小时清理误删
- **修复**：JSON 拼接漏引号导致消息显示失败

---

## 常见问题

### Q：浏览器提示「你的连接不是私密连接」？
自签名证书正常现象，点击「高级」→「继续访问」。

### Q：`setup.bat` 闪退？
右键「以管理员身份运行」，或从命令行执行查看错误。

### Q：消息显示不出来？
检查浏览器 Console（F12）是否有 `JSON` 报错，可能是**旧数据库有脏数据**。停掉服务器，删除 `data/` 目录，重启。

### Q：ngrok 地址每次重启都变？
免费版正常。保持 ngrok 长时间运行，或升级付费固定域名。

### Q：数据存在哪？
全部在 `data/chat.db`（SQLite）。清空所有数据：删除 `data/` 目录，重启。

### Q：想迁移到别的电脑？
1. 拷贝整个项目文件夹（不含 `data/`、`.idea/`、`target/`、`keystore.p12`）
2. 新电脑装 JDK 21+
3. 双击 `setup.bat` 生成新证书
4. 启动 `ChatServer`
5. 配置 ngrok

---

## 技术栈

- Java 21 原生 `com.sun.net.httpserver.HttpsServer`
- 虚拟线程（JDK 21）
- SSE（Server-Sent Events）实时推送
- SQLite（sqlite-jdbc）+ WAL 模式
- PBKDF2WithHmacSHA256 密码哈希
- 前端原生 HTML / CSS / JavaScript
- Canvas 图片压缩
- Maven 构建 + shade 打包
- ngrok 内网穿透

---

## 安全提醒

1. `keystore.p12` 含私钥，**不要提交到公开仓库**
2. `data/chat.db` 含所有用户数据，**不要上传**
3. 使用 ngrok 时任何拿到地址的人都能访问，演示完按 `Ctrl + C` 关闭
4. 管理员密码上线前务必修改
5. 校园网可能禁止私设服务器和内网穿透，先看校规

---

## 许可证

仅供学习交流使用。
