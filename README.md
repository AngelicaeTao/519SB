# 519SB 聊天室

基于 Java 21 原生 HTTPS + SSE 的简易聊天室 + 论坛，支持账号系统、实时聊天、图片消息、帖子系统、个人中心、管理员控制台。外网访问统一通过 ngrok 转发。

## 三分钟快速上手

1. 装 JDK 21+（https://adoptium.net/）
2. 双击 `setup.bat` 生成证书
3. 用 IDEA 打开项目，运行 `BootstrapLayer.ChatServer`
4. 另开命令行运行 `ngrok http https://localhost:8443 --region ap`
5. 把 ngrok 给的 `https://xxx.ngrok-free.app` 发给朋友

**不用改任何配置，默认就能跑。**

或者直接**双击 `start.bat`** 一键启动（自动编译 + 启动 ngrok + 启动服务器）。

启动完成后服务器会**自动打开浏览器**：先向 ngrok 的本地 API（`127.0.0.1:4040`）要公网地址，打开 `<公网地址>/login`；检测不到 ngrok 时退回 `https://localhost:8443/login`。想关掉或改目标页面见下方配置项。

> 证书是自签名，浏览器首次访问会提示不安全，选择继续访问即可。

---

## 功能特性

### 基础
- 门禁验证码（首次访问需输入，通过后 30 天免输）
- 账号注册、登录（密码要求：6~50 位，必须含字母和数字）
- 登录频率限制 + 管理员登录锁定，防爆破
- 单设备登录（新设备登录会踢掉旧设备）
- 密码 PBKDF2 + 随机盐存储，不存明文
- 改密码后**其它设备全部下线**（旧 token 立即失效，当前设备自动换发新 token）

### 聊天室
- 文字消息 SSE 实时推送
- 图片发送（前端自动压缩到最大 800px，JPEG 质量 0.6）
- 图片点击放大预览，支持滚轮缩放、拖动、下载
- 头像显示（每条消息带头像、昵称、时间戳）
- **消息撤回**（自己发的消息 2 分钟内可撤回，双方同步删除）
- **@提醒**（输入 `@` 弹出在线用户列表，被 @ 的人收到高亮 + 提示音）
- **未读计数**（切到后台时有新消息，标签标题显示 `(3) 519SB-Chat`）
- **消息分页**（滚到顶部自动加载更早的消息，每次 30 条）
- **在线用户抽屉**（点击右上角"在线 N"展开）
- **消息筛选**（按用户搜索、按日期跳转、按类型过滤）
- **在线状态按最近活动判定**（60 秒内有请求即算在线），所以切到帖子页不会掉线
- 右侧悬浮按钮（回顶 / 筛选 / 回底）鼠标不在上面时自动淡化，不挡视线
- 支持 Shift + Enter 换行

### 帖子论坛
- 发布帖子（标题 + 正文，支持长文）
- 帖子列表（标题、摘要、作者、时间、浏览量）
- 帖子详情（阅读全文、浏览量自动计数）
- **点赞**（每人每帖一次，可取消）
- **评论与回复**（支持二级回复，显示"回复 @某人"）
- 编辑 / 删除自己的帖子
- 作者显示昵称（不显示账号）

### 个人中心
- 修改昵称（与账号分离）
- 上传/更换头像（永久保留，不会自动清理）
- 修改密码（**必须验证旧密码**，成功后其它设备强制下线）

### 管理员控制台
- 查看所有用户（账号、昵称、身份、注册时间）
- 编辑用户（昵称、密码、头像、管理员权限）
- 删除用户（**在线用户强制踢出**，同时删除其所有消息）
- 查看指定用户的所有消息
- 单条 / 批量删除消息
- **清空所有聊天消息**（保留用户和头像）
- 默认管理员保护：不能删除、不能取消管理员权限

---

## 安全与防护

- HTTPS 加密（自签名证书）
- 密码 PBKDF2WithHmacSHA256 + 随机盐（迭代 10000 次）
- Token 会话 30 天过期（可配置）
- **Session 不存明文密码，SQLite 持久化**，重启服务器不掉登录

**访问控制**

- **HTTP 方法校验**：所有写接口显式限定 POST/GET（部分在 handler 内判定路径+方法，部分用统一的 `rejectWrongMethod` 助手），杜绝"一个链接或一张图片就能触发删库/删帖"
- **跨站写请求防护**：写操作校验 `Origin` 与 `Host` 同源，异源一律 403
- **可信代理白名单**：仅当直连来自 `trusted.proxies` 时才采信 `X-Forwarded-For`，否则 IP 限流可被伪造头绕过
- 头像 ID、图片 ID 后端格式校验，防 XSS 与路径问题
- 前端全部使用 DOM API，无 `innerHTML` 拼接用户内容

**限流与防 DoS**

- **IP 级限流**：每分钟 300 次请求/IP
- **并发限制**：每 IP 30 并发，全局 500 并发
- **请求体大小限制**：普通 64KB，图片上传 10MB
  - 关键点：限制在**流式读取时按实际字节数强制生效**，不依赖 `Content-Length` 头；否则用 `Transfer-Encoding: chunked` 不带长度头即可绕过，用超大请求体耗尽内存
- **管理员登录锁定**：失败 3 次锁 10 分钟
- **注册频率限制**：每 IP 每小时 5 次
- **SSE 连接数限制**：每用户 20 个，每 IP 10 个，全局 500 个
  - 超限时在发响应头之前就拒绝，不会先把连接交出去
  - 预占连接名额后若握手失败会归还名额，避免半连接耗尽额度
- **SSE 空闲超时**：默认 10 分钟无活动自动断开，回收僵尸长连接
- **SSE 广播同步写入**：不对每个客户端提交独立任务，避免"连接数 × 消息频率"造成无界线程提交
- **图片上传配额**：每用户最多 300 张 / 200MB，防止单个账号灌满磁盘
- **限流器计数表有上限**：超过后新来源共用溢出桶，避免伪造来源撑爆内存

**响应加固**

- 统一安全响应头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: same-origin`、`Strict-Transport-Security`、`Content-Security-Policy`
- JSON 响应带 `Cache-Control: no-store`
- 图片按**文件头魔数**校验真实类型（JPEG/PNG/GIF/WebP），按真实类型返回 `Content-Type`，认不出的一律 415

---

## 配置项（`config.properties`）

大部分项保持默认即可。常用的几个：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `port` | `8443` | 服务端口，ngrok 转发到这里 |
| `access.code` | — | 门禁验证码，**建议改掉** |
| `admin.password` | `admin123` | 默认管理员密码，**首次登录后立刻改** |
| `auto.open.browser` | `true` | 启动后是否自动打开浏览器；无图形界面环境（服务器、Docker）改成 `false` |
| `auto.open.path` | `/login` | 自动打开的目标页面，可改 `/chat` 或 `/posts` |
| `trusted.proxies` | `127.0.0.1,::1` | 可信反向代理地址。换成 Cloudflare / frp / nginx 部署时**必须**把前置地址加进来，否则 IP 限流可被伪造头绕过 |
| `max.image.bytes.per.user` | `209715200` | 单用户图片总字节上限（200MB） |
| `max.image.count.per.user` | `300` | 单用户图片条数上限 |
| `sse.idle.timeout.ms` | `600000` | SSE 空闲超时（10 分钟） |
| `session.ttl.days` | `30` | 登录状态有效期 |
| `max.history` | `200` | 聊天历史保留条数 |

完整清单见 `config.properties.example`。

---

## 已知的薄弱点

以下问题**尚未修复**，请按自己的部署环境评估：

- **`access.code` 与 `admin.password` 是配置文件里的明文默认值**，随仓库分发。外网可达时这是最容易的入口，务必改掉。
- **Session token 走 URL query 传递**，会进入浏览器历史、ngrok 请求日志。抓到一条日志即可在有效期内接管账号。
- **PBKDF2 迭代次数为 10000**，低于 OWASP 现行建议（约 600k）。数据库泄露时离线爆破成本较低。
- **门禁是客户端一个标记位**，手工设置 cookie 即可绕过；且帖子页与部分接口不做门禁检查。
- **`data/chat.db` 未加密**，聊天记录、图片、密码哈希、session token 都在同一个文件里。

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
src/main/java/
├── BootstrapLayer/      ChatServer        服务器启动、路由注册、限流包装
├── ConfigurationLayer/  Config            配置加载
├── HandlerLayer/        AuthHandler       门禁、注册、登录、登出
│                        ChatHandler       聊天、SSE、历史、在线、用户目录
│                        PostHandler       帖子 CRUD、点赞、评论
│                        ProfileHandler    昵称、头像、改密码
│                        ImageHandler      图片上传下载
│                        AdminHandler      管理员控制台接口
├── InfrastructureLayer/ HttpUtil          HTTP 工具、JSON 转义、限长读取、安全头
│                        SessionManager    会话管理（SQLite 持久化）
│                        SseManager        SSE 连接池、广播、踢人
│                        IpRateLimiter     IP 限流、并发、管理员锁定
│                        RateLimiter       按账号/用户的滑动窗口限流
│                        DatabaseManager   SQLite 连接与建表迁移
├── DataLayer/           UserStore         用户与密码哈希
│                        MessageStore      聊天消息
│                        PostStore         帖子、点赞、评论
└── ViewLayer/           WebPages          聊天页
                         PostPage          论坛页
                         AdminPage         管理页
                         ProfilePage       个人中心
                         LoginPage         登录页
                         GatePage          门禁页
```
