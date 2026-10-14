# 519SB 聊天室

基于 **Java 21 原生 HTTPS + SSE** 的聊天室 + 论坛。零前端构建、零框架依赖，只用 JDK 自带的 `com.sun.net.httpserver` 和 SQLite。外网访问通过 ngrok 转发。

---

## 三分钟快速上手

1. 装 JDK 21+（<https://adoptium.net/>）
2. 双击 `setup.bat` 生成自签名证书
3. 双击 `start.bat` 一键启动（自动编译 + 起 ngrok + 起服务器）

或者手动来：

```bash
# 用 IDEA 打开项目，运行 BootstrapLayer.ChatServer
# 另开一个命令行
ngrok http https://localhost:8443 --region ap
# 把 ngrok 给出的 https://xxx.ngrok-free.app 发出去
```

**不用改任何配置，默认就能跑。** 启动完成后会自动打开浏览器：先向 ngrok 本地 API（`127.0.0.1:4040`）要公网地址并打开 `<公网地址>/login`，检测不到 ngrok 时退回 `https://localhost:8443/login`。

> 证书是自签名，浏览器首次访问会提示不安全，选择继续访问即可。
>
> 用 ngrok 域名的另一个好处：它是真实 HTTPS，**浏览器会允许使用麦克风**（`getUserMedia` 要求安全上下文），语音消息才能录。

---

## 项目结构

```
519SB/
├── src/main/java/
│   ├── BootstrapLayer/       ChatServer：启动、路由注册、限流包装
│   ├── ConfigurationLayer/   Config：全部配置项
│   ├── HandlerLayer/         各 HTTP 处理器（Auth / Chat / Post / Image / Profile / Admin / User）
│   ├── DataLayer/            数据访问（User / Message / Post / ImageStore）
│   ├── InfrastructureLayer/  基础设施（HttpUtil、Session、SSE、限流、主题、流量、模板加载）
│   └── ViewLayer/            页面常量（转发到资源文件）
├── src/main/resources/web/   ★ 前端资源：页面模板与共用脚本
│   ├── chat.html             聊天室
│   ├── login.html            登录 / 注册
│   ├── gate.html             门禁
│   ├── post-list.html        帖子列表
│   ├── post-detail.html      帖子详情 + 评论
│   ├── profile.html          个人中心
│   ├── user.html             他人主页
│   ├── admin.html            管理员控制台
│   ├── theme.js              配色（读 localStorage 注入 CSS 变量）
│   └── media.js              媒体上传（动图识别、大文件原始二进制上传）
├── config.properties.example 配置模板
├── ROADMAP.md                进度与待办
└── start.bat / setup.bat
```

### 前端为什么是资源文件

页面模板**不再编译进 class**，而是放在 `src/main/resources/web/` 运行时读取。原因：

- JVM 单个字符串常量上限 **65535 字节**。页面继续长大必然撞上，而 `javac` 报的是极具误导性的「未闭合字符串文字」——这个坑踩过两次。
- 改 UI 不必重新编译，刷新即可。
- `chat.html` 有完整的 HTML/CSS/JS 语法高亮，diff 可读。

`InfrastructureLayer.WebResources` 负责加载：先走 classpath（打包成 jar 时），失败则回退到 `src/main/resources/web/` 相对路径（`start.bat` 手工编译运行时）。找不到模板时返回一个可读的错误页，**不会让服务起不来**。

---

## 功能特性

### 基础
- 门禁验证码（通过后 30 天免输）
- 注册 / 登录（密码 6~50 位，须含字母和数字）
- 登录频率限制 + 管理员登录锁定，防爆破
- 单设备登录（新设备登录踢掉旧设备）
- 密码 PBKDF2 + 随机盐，不存明文
- Session 存 SQLite，**重启不掉登录**

### 聊天室
- 文字消息 SSE 实时推送
- **五种消息类型**：文字 / 图片 / 动图 / 语音 / 文件
- 图片与动图：静态图前端压缩至 800px JPEG 0.6；**GIF / 动态 WebP 原样上传以保留动画**
- 语音：点击麦克风录制（最长 60 秒），气泡内播放
- 文件：任意文件分享，**保留原始文件名**，点击下载
- 图片点击放大预览（滚轮缩放、拖动、下载）
- **消息回复**：悬停出现「回复」，输入框上方显示引用条，气泡内渲染引用块，点击可跳到被回复的消息
- **消息撤回**（2 分钟内可撤回，双方同步删除）
- **@提醒**：输入 `@` 弹出在线用户补全，被 @ 的人收到高亮 + 提示音
- **未读计数**（后台时标签标题显示 `(3) 519SB-Chat`）
- **消息分页**（滚到顶自动加载更早的 30 条）
- **在线用户抽屉**（SSE 实时推送，零轮询）
- **日历跳转**：年月都是下拉框，可键盘输入年份；有消息的日期有标识，点击跳到当天

### 帖子论坛
- 发帖（标题 + 正文 + 配图）、编辑、删除
- 列表（标题、摘要、缩略图、作者、时间、浏览量、点赞数）
- 详情（全文、浏览量自动计数）
- **点赞**（每人每帖一次，可取消）
- **评论与二级回复**，显示「回复 @某人」
- **评论支持配图**（每条最多 3 张）
- **帖子搜索**：按标题 / 内容 / 作者（作者同时匹配账号与昵称）
- **列表自动刷新**：有人发帖/改帖/删帖时，其它人的列表通过 SSE 自动更新（防抖，编辑中不打断，滚动位置保持）

### 个人中心与主页
- 修改昵称（与账号分离），**记录并展示历史昵称**（含相对时间）
- 上传 / 更换头像
- 修改密码（**必须验证旧密码**，成功后其它设备强制下线并换发新 token）
- **个性化配色**：6 套主题，一键切换，全站生效
- **他人主页**：点任意头像或昵称进入，展示头像、昵称、注册时间、发言统计、历史昵称

### 管理员控制台
- 查看所有用户（账号、昵称、身份、注册时间）
- 编辑用户（昵称、密码、头像、管理员权限）
- 删除用户（在线强制踢出，同时删除其所有消息）
- 查看指定用户的所有消息
- 单条 / 批量删除消息、清空所有聊天消息
- 默认管理员保护：不能删除、不能取消管理员权限
- `/admin` 页面鉴权，非管理员跳登录页

### 运维
- **流量监测**：按自然月统计收发字节（含 SSE 推送与媒体下载），按用户拆分，每 5 分钟在控制台打印汇总，内存累积 + 定期落盘（重启不丢）
- **媒体配额**：按用途（聊天图/头像/帖子图/评论图/语音/动图/文件）分别计数，互不挤占
- 控制台可配自动打开浏览器，优先打开 ngrok 公网地址

---

## 安全与防护

**认证与会话**

- 密码 PBKDF2WithHmacSHA256 + 随机盐
- Token 会话 30 天过期（可配），存 SQLite
- 改密码后吊销该用户全部旧会话并换发新 token
- 单设备登录

**访问控制**

- **HTTP 方法校验**：所有写接口显式限定方法，杜绝"一个链接或一张图片就能触发删库/删帖"
- **跨站写请求防护**：写操作校验 `Origin` 与 `Host` 同源，异源一律 403
- **附件归属校验**：发消息/发评论时提交的是媒体 id，服务端确认「存在 + 属于你 + 用途匹配」，否则 403。否则任何人都能引用别人的图片/语音
- **可信代理白名单**：仅当直连来自 `trusted.proxies` 时才采信 `X-Forwarded-For`，否则 IP 限流可被伪造头绕过
- 媒体 id、头像 id 后端格式校验（32 位小写 hex）
- 上传文件**强制以附件下载**：`file` 类型一律 `application/octet-stream` + `Content-Disposition: attachment`，**绝不内联渲染**——上传的 `.html`/`.svg` 若被 inline 返回就是一个存储型 XSS
- 上传文件名净化：去掉路径成分、控制字符、引号（防目录穿越式命名与响应头注入）
- 前端全部使用 DOM API，无 `innerHTML` 拼接用户内容；服务端渲染的用户数据一律经 `escapeHtml`
- 统一安全响应头：`nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy`、HSTS、CSP

**限流与防 DoS**

- IP 级限流：每分钟 300 次请求/IP
- 并发限制：每 IP 30，全局 500
- 请求体上限：普通 64KB，上传 25MB
  - 关键点：限制在**流式读取时按实际字节数强制生效**，不依赖 `Content-Length`；否则用 `Transfer-Encoding: chunked` 不带长度头即可绕过
- 管理员登录锁定：失败 3 次锁 10 分钟
- 注册频率限制：每 IP 每小时 5 次
- SSE 连接数限制：每 IP / 全局各有上限，并有空闲超时回收
- 单文件大小按用途限制；每用户每用途的条数与字节上限
- 限流器计数表有上限，防内存膨胀

**已知弱点**（未修，按需处理）

- 门禁码与默认管理员密码为明文配置，默认值可直接跑起来
- Session token 走 URL query 传递（同一来源内，`Referrer-Policy: same-origin` 限制外泄）
- PBKDF2 迭代 10000 次，低于现代推荐值
- `data/chat.db` 未加密，落盘可见

---

## 配置项

把 `config.properties.example` 复制为 `config.properties` 再改。

### 基础

| 键 | 默认 | 说明 |
|---|---|---|
| `port` | 8443 | 监听端口 |
| `site.title` | 519SB-Chat | 页面标题 |
| `keystore.path` | keystore.p12 | 证书文件 |
| `keystore.password` | changeit | 证书密码 |
| `db.file` | data/chat.db | SQLite 路径 |
| `thread.pool.size` | 16 | HTTP 线程池 |
| `access.code` | 519666233 | 门禁验证码 |
| `session.ttl.days` | 30 | 会话有效期 |
| `max.history` | 200 | 聊天记录保留条数 |

### 限额与限流

| 键 | 默认 | 说明 |
|---|---|---|
| `login.max.attempts` / `login.window.ms` | 5 / 60000 | 登录失败限制 |
| `send.max.count` / `send.window.ms` | 5 / 3000 | 发消息频率 |
| `max.image.size` | 5MB | 单图上限 |
| `image.max.width` / `image.quality` | 800 / 0.6 | 前端压缩参数 |
| `max.media.size.<type>` | 见下 | 单文件上限，按用途 |
| `sse.idle.timeout.ms` | 600000 | SSE 空闲断开 |
| `trusted.proxies` | 127.0.0.1,::1 | 可信反代地址 |

### 上传配额（每用户，按用途独立计数）

| 用途 | 条数 | 字节 | 单文件 | 保留 |
|---|---|---|---|---|
| `message` 聊天图 | 300 | 200MB | 5MB | 24 小时 |
| `avatar` 头像 | 10 | 5MB | 5MB | 永久（只留最近 5 张） |
| `post` 帖子图 | 300 | 200MB | 5MB | 永久 |
| `comment` 评论图 | 200 | 150MB | 5MB | 永久 |
| `voice` 语音 | 100 | 100MB | 5MB | 7 天 |
| `anim` 动图 | 100 | 100MB | 5MB | 永久 |
| `file` 文件 | 100 | 500MB | 20MB | 永久 |

配置键形如 `max.image.count.per.user.<用途>` 与 `max.image.bytes.per.user.<用途>`；
不带用途后缀的旧键**只对 message / post 生效**（历史行为）。
保留期用 `image.retention.ms.<用途>` 覆盖。

> 帖子图和评论图**不建议**设保留期——帖子/评论永久保留，媒体被删了就成了裂图。
> 聊天图和语音可以设，因为聊天记录本身有条数上限，旧消息本来就会被裁掉。

### 启动行为

| 键 | 默认 | 说明 |
|---|---|---|
| `auto.open.browser` | true | 启动后自动开浏览器 |
| `auto.open.path` | /login | 自动打开的目标路径 |

---

## 环境要求

| 软件 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | 必须 ≥ 21，用了虚拟线程与文本块 |
| Maven | 3.8+ | 仅用于拉依赖，IDEA 自带 |
| ngrok | 最新版 | 外网访问与麦克风权限必需 |

依赖只有三个：`sqlite-jdbc`、`slf4j-api`、`slf4j-simple`（见 `pom.xml`）。

```bash
java -version
javac -version
keytool -help
```

---

## 常见问题

**浏览器提示"不安全"**
自签名证书的正常现象。点「高级」→「继续访问」。或用 ngrok 的公网域名，那是真实证书。

**麦克风不能用 / 语音录不了**
`getUserMedia` 要求**安全上下文**。必须走 HTTPS（ngrok 域名或 `localhost`）。如果是 `http://` 或局域网 IP 访问，浏览器会直接拒绝。

**图片/语音/文件发不出去，报 403**
附件归属校验拦下了。常见原因是登录已过期——重新登录后再试。

**上传大文件报「Failed to fetch」**
这个提示表示**请求没完成**，不是服务器返回的错误。最常见的原因是**上传途中刷新/关闭了页面**——浏览器会中止在途请求。上传时输入框上方有进度条，传完再操作。

**页面样式不对 / 模板缺失**
服务器日志会出现 `[WebResources] 找不到模板`。确认 `src/main/resources/web/` 存在，且服务的工作目录是项目根目录（`start.bat` 已处理）。

**改了 `chat.html` 要重新编译吗**
不用。它是运行时读取的资源文件，重启服务或刷新页面即可。

---

## 开发提示

- **改前端**：直接编辑 `src/main/resources/web/` 下的文件，不用编译。
- **改 Java**：重新 `javac` 到 `out/` 或让 IDEA 编译，然后重启。
- **加数据库列**：`ALTER TABLE` 必须放在该表的 `CREATE TABLE` **之后**。放在前面时新库里表还不存在，ALTER 会失败并被静默忽略，导致全新部署缺列——这个坑踩过。
- **加 SSE 事件类型**：`chat.html` 的 `onmessage` 末尾有 `appendMessage` 兜底分支，未知事件会被当成聊天消息渲染。**新增事件必须在兜底之前显式忽略或处理**。
- **加页面**：模板放 `src/main/resources/web/`，视图常量用 `WebResources.load("xxx.html")`，处理器里用 `HttpUtil.sendHtmlThemed(...)` 传用户配色。
