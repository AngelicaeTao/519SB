# 519SB 聊天室

基于 **Java 21 原生 HTTPS + SSE** 的聊天室 + 论坛。零前端构建、零框架依赖，只用 JDK 自带的 `com.sun.net.httpserver` 和 SQLite。外网访问通过 ngrok 转发。

**目录**：[安装与启动](#安装与启动) · [项目结构](#项目结构) · [功能特性](#功能特性) · [传输与缓存](#传输与缓存) · [安全与防护](#安全与防护) · [配置项](#配置项) · [运行环境](#运行环境) · [常见问题](#常见问题) · [开发提示](#开发提示) · [踩过的坑](#踩过的坑)

---

## 安装与启动

### 前置条件

| 软件 | 必需性 | 用途 |
|---|---|---|
| **JDK 21+** | 必需 | 编译与运行。用了虚拟线程（`newVirtualThreadPerTaskExecutor`）和文本块，21 以下直接编不过 |
| **Maven 3.8+** | 可选 | 只为拉依赖和打包成 jar。不想装 Maven 也可以手动下三个 jar |
| **ngrok** | 可选 | 手机/外网访问。**也是麦克风权限的前提**：`getUserMedia` 要求安全上下文，自签名证书虽然能进页面，但 `https://localhost` 之外只有真证书域名才稳 |

```bash
java -version      # 必须 21 或更高
javac -version
keytool -help      # 生成自签名证书用，JDK 自带
ngrok version      # 可选
```

### 方式 A：Windows 一键（最省事）

1. 装 JDK 21（<https://adoptium.net/>），确认 `bin` 在 PATH 里
2. 从 <https://ngrok.com/download> 下载 `ngrok.exe`，放到**项目根目录**，首次用要绑定账号：
   ```bat
   ngrok config add-authtoken <你的 token>
   ```
3. 双击 **`setup.bat`**：生成自签名证书 `keystore.p12`，并创建 `data/` 目录
4. 双击 **`start.bat`**：自动完成编译与启动

`start.bat` 实际做三件事，出问题可以按这三步排查：

| 步骤 | 做什么 | 常见失败 |
|---|---|---|
| 1 | 从 `%USERPROFILE%\.m2\repository` 找 `sqlite-jdbc` / `slf4j-api` / `slf4j-simple` 三个 jar | 找不到 → 先用 IDEA 打开项目让 Maven 下载依赖，或手动 `mvn dependency:resolve` |
| 2 | `javac -encoding UTF-8 -d out` 全量编译 | 编码报错 → 确认 JDK 是 21+，且用 `-encoding UTF-8` |
| 3 | 新开两个窗口：ngrok（ap 节点）+ ChatServer | 端口被占 → 改 `port` 配置或先杀掉旧的 java 进程 |

启动后会自动打开浏览器：先向 ngrok 的本地 API（`127.0.0.1:4040`）要公网地址并打开 `<公网地址>/login`，取不到就退回 `https://localhost:8443/login`（由 `auto.open.browser` / `auto.open.path` 控制）。

### 方式 B：Maven 打包成单个 jar

```bash
mvn clean package
java -jar target/519SB-1.0.jar
```

jar 里已包含全部依赖与页面模板（shade 插件，主类 `BootstrapLayer.ChatServer`）。

> **工作目录必须是项目根目录**。程序读的是相对路径：`config.properties`、`keystore.p12`、`data/chat.db`。在别的目录执行 `java -jar`，它会当成全新部署——建一个空库、找不到证书直接起不来。

### 方式 C：手动 javac（Linux / macOS / 不装 Maven）

```bash
# 1) 依赖：三个 jar 放到 lib/
mvn dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime
#    不想装 Maven 就直接下这三个，放进 lib/：
#      https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.3.0/sqlite-jdbc-3.45.3.0.jar
#      https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar
#      https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar

# 2) 证书（已存在就跳过）
keytool -genkeypair -alias myserver -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore keystore.p12 -storepass changeit -validity 365 \
  -dname "CN=519SB-chat-room" -ext "SAN=IP:127.0.0.1,DNS:localhost"
#    SAN 不能省：没有它浏览器按"主机名不匹配"报错，比自签名更烦

# 3) 编译
mkdir -p out && find src/main/java -name '*.java' > /tmp/srcs.txt
javac -encoding UTF-8 -d out -cp "lib/*" @/tmp/srcs.txt

# 4) 运行（Linux / macOS 用 : 分隔；Windows 把 : 换成 ; 并改用 lib\*）
java -cp "out:lib/*:src/main/resources" BootstrapLayer.ChatServer

# 5) 另开一个终端起隧道
./ngrok http https://localhost:8443 --region ap
```

`src/main/resources` 必须出现在 classpath 里，否则页面模板找不到（日志报 `[WebResources] 找不到模板`）。

### 第一次启动会做什么

| 事项 | 说明 |
|---|---|
| 建库建表 | `data/chat.db` 不存在时自动创建，父目录也会自动 `mkdirs` |
| 建管理员 | 用 `admin.username` / `admin.password`（默认 `admin` / `admin123`）创建，已存在则跳过 |
| 监听地址 | 固定 `127.0.0.1:8443`，**不对外网开放**；外网一律走 ngrok |
| 证书 | `keystore.p12` 缺失会直接启动失败，先跑 `setup.bat` 或上面的 keytool 命令 |

### 配置

**默认零配置就能跑。** 要改就复制模板：

```bash
cp config.properties.example config.properties
```

`config.properties` 含门禁码与管理员密码，已在 `.gitignore` 里，**不要提交**。完整配置项见[配置项](#配置项)。

---

## 项目结构

```
519SB/
├── src/main/java/
│   ├── BootstrapLayer/       ChatServer：启动、路由注册、限流包装、定时任务
│   ├── ConfigurationLayer/   Config：全部配置项与默认值
│   ├── HandlerLayer/         各 HTTP 处理器（Auth / Chat / Post / Image / Profile / Admin / User / Screen）
│   ├── DataLayer/            数据访问（User / Message / Post / ImageStore）
│   ├── InfrastructureLayer/  基础设施（HttpUtil、Session、SSE、限流、主题、流量、模板加载）
│   └── ViewLayer/            页面常量（转发到资源文件）
├── src/main/resources/web/   ★ 前端资源：页面模板与共用静态资源
│   ├── chat.html             聊天室（含筛选面板、屏幕共享、语音气泡）
│   ├── login.html            登录 / 注册
│   ├── gate.html             门禁
│   ├── post-list.html        帖子列表
│   ├── post-detail.html      帖子详情 + 评论
│   ├── profile.html          个人中心
│   ├── user.html             他人主页
│   ├── admin.html            管理员控制台
│   ├── app.css               ★ 全站视觉层：移动底栏 / PC 侧栏、暗色主题、组件样式
│   ├── app.js                ★ 登录态守卫 + 导航栏注入（必须在 <head> 里、页面脚本之前引入）
│   ├── theme.js              配色（读 localStorage / 服务端主题，注入 CSS 变量）
│   └── media.js              媒体上传（动图识别、大文件原始二进制上传）
├── data/                     SQLite 数据库（chat.db），首次启动自动创建
├── out/                      javac 编译输出（不入库）
├── target/                   Maven 构建输出（不入库）
├── config.properties.example 配置模板（入库）；config.properties 是本机配置（不入库）
├── keystore.p12              HTTPS 自签名证书（不入库，setup.bat 生成）
├── ROADMAP.md                进度与待办
└── start.bat / setup.bat     Windows 一键脚本
```

### 前端资源怎么被加载

| 资源 | 加载方式 | 改动后生效方式 |
|---|---|---|
| `*.html` | `ViewLayer/WebPages` 里 `static final` 常量，**类加载时读一次** | 必须重启 JVM |
| `app.css` / `app.js` / `theme.js` / `media.js` | `WebResources.load()` 每次请求重读文件 | 递增 `?v=` 版本号后刷新页面 |

四个静态资源由 `ChatServer` 单独注册路由（`/app.css`、`/app.js`、`/theme.js`、`/media.js`），不在某个页面的处理器里，所以所有页面共用同一份、天然共享浏览器缓存。

### 前端为什么是资源文件

页面模板**不再编译进 class**，而是放在 `src/main/resources/web/` 运行时读取。原因：

- JVM 单个字符串常量上限 **65535 字节**。页面继续长大必然撞上，而 `javac` 报的是极具误导性的「未闭合字符串文字」——这个坑踩过两次。
- 改 UI 不必重新编译：CSS/JS 改完递增 `?v=` 版本号后刷新即可；HTML 需重启服务（`WebPages` 里的常量是类加载时读取的）。
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
- **筛选面板**：按用户（昵称/账号搜索或从在线列表选）、按日期（日历跳转）、按类型（文字 / 图片 / 语音 / 文件，可多选）过滤当前会话，标题实时显示命中条数，浮动按钮上带筛选计数徽标
- **语音气泡**：自绘波形条 + 播放键 + 时长，时长用 WebAudio `decodeAudioData` 从音频数据里算真实值（不采信 WebM 头里那个边录边写造成的垃圾 Duration）
- **屏幕共享**（见下）

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

### 屏幕共享

**方案是「定时截屏推流」，不是 WebRTC**：`getDisplayMedia` 抓屏 → canvas 缩放 → 导出 JPEG → `POST /screen` → 服务器用 `multipart/x-mixed-replace` 转发给观看者。

选它的理由：纯 HTTP，能直接穿 ngrok，不需要 STUN/TURN 打洞，也不用处理信令。代价是**流量远大于 WebRTC**——这是硬伤，调参只能缓解。

| 配置键 | 默认 | 含义 |
|---|---|---|
| `screen.fps` | 15 | 目标帧率。屏幕内容大部分时间静止，15 帧肉眼差别不大，但流量比 30 帧少一半 |
| `screen.max.width` | 1280 | 缩放后最大宽度，越小越省也越糊 |
| `screen.quality` | 0.5 | JPEG 质量 |
| `screen.max.viewers` | 5 | 同时观看人数上限（每路都要服务器复制帧） |
| `screen.max.frame.bytes` | 524288 | 单帧上限，防借接口塞大请求体 |
| `screen.idle.timeout.ms` | 6000 | 多久收不到新帧就判定分享者掉线、释放占用 |
| `screen.viewer.timeout.ms` | 12000 | 观看者心跳超时后踢掉（防半开连接持续烧流量） |

行为约定：

- **同一时刻只有一个人能分享**，靠占用标记实现，掉线有 6 秒兜底回收
- **无人观看时自动暂停推流**，有人点「观看」再恢复
- 观看者异常断开（合盖、断网、强杀浏览器）时 TCP 会半开，服务器往那里写帧不会立刻报错，
  所以观看端要定期发心跳，超时就踢

> **带宽别抱幻想**：15 fps、每帧 30~60 KB 时，需求是 0.5~1 MB/s，
> 而 ngrok 免费隧道实测只有 ~50 KB/s，差一个数量级。
> 真要用，把 `screen.fps` 调到 2~5、`screen.max.width` 调到 960 以下，
> 或者在局域网 / 独立服务器上跑。

### 界面与适配

- **一套 DOM，两种导航**：`app.js` 同时挂载底部 tabbar 与左侧 sidenav，靠 CSS 互斥显隐
- **断点 900px**：小于 900 显示底部 tabbar（移动端）；大于等于 900 隐藏 tabbar、左侧固定 236px 侧栏（PC 端）。拖动窗口跨断点无需刷新
- **侧栏内容**：品牌区、首页 / 聊天室 / 发布新帖 / 在线用户 / 个人中心 / 管理后台，底部是当前用户与退出
- **内容区宽度**：帖子列表 1240px（≥1400px 时 1420px）、普通页 880px、正文 760px、聊天室 920px 居中
- **6 套配色**：绿 / 蓝 / 紫 / 橙 / 粉 / 红。个人中心一键切换，全站走 CSS 变量；配色同时存在服务端，页面渲染时就带上，避免首屏闪一下默认色
- 触摸设备单独处理（`@media (hover: none)`），避免点完按钮一直停在 hover 态

### 运维

- **流量监测**：按自然月统计收发字节（含 SSE 推送与媒体下载，按**压缩后**的实际字节计），
  按用户拆分，内存累积 + 每 30 秒落盘 + 每 5 分钟在控制台打印当月汇总（重启不丢）
- **媒体配额**：按用途（聊天图 / 头像 / 帖子图 / 评论图 / 语音 / 动图 / 文件）分别计数，互不挤占
- **定时任务**（单个调度线程，各任务按自己的频率执行）：

  | 任务 | 频率 | 作用 |
  |---|---|---|
  | 会话清理 | 5 分钟 | 删除过期 token |
  | 限流器清理 | 5 分钟 | 回收计数表里的过期窗口，防内存膨胀 |
  | 流量落盘 | 30 秒 | 内存计数写回 SQLite |
  | 流量汇总打印 | 5 分钟 | 控制台输出当月收发量 |
  | SSE 回收 | 3 秒 | 踢掉写不出去的连接、对账在线列表 |
  | 屏幕共享对账 | 3 秒 | 踢超时观看者、校正观看人数、释放掉线分享者的占用 |
  | 媒体清理 | 每次上传后 | 按保留期删除过期媒体（默认全部永久，见配置项） |

- 控制台可配自动打开浏览器，优先打开 ngrok 公网地址

### 数据与备份

**所有媒体都存在数据库里**，没有独立的图片/语音目录：

```
data/chat.db
├── users / sessions / messages / posts / post_comments / post_likes / nickname_history
└── images          ← 图片、语音、文件、头像、帖子配图的二进制全在这张表（data 列是 BLOB）
```

`messages` 里的图片消息只是一行记录 + 一个媒体 id，真正的字节在 `images` 表。
所以**备份只拷 `chat.db` 一个文件就够**，但也意味着：`images` 表里删掉一行，那条消息就变成裂图。

```bash
# 推荐：让 SQLite 自己做一致性快照（服务运行中也能用）
sqlite3 data/chat.db ".backup data/chat-$(date +%Y%m%d).db"

# 服务已停时直接复制也安全
cp data/chat.db data/chat.db.bak-20261123
```

| 注意点 | 说明 |
|---|---|
| `-wal` / `-shm` | 存在这两个文件说明有未落盘的事务。直接 `cp chat.db` 可能拿到不一致快照，优先用 `.backup` |
| 换机器 | 拷 `data/chat.db` + `keystore.p12` + `config.properties`（没有配置就用默认值）。页面模板和代码走代码仓库 |
| 删库重来 | 停服后删掉 `data/chat.db`，启动会自动重建空库并创建默认管理员 |
| 只删某类媒体 | 不要手删 `images` 里被 `messages.text` 引用的行——消息记录还在，图就裂了 |

---

## 传输与缓存

这一节解释「为什么经公网隧道访问会卡」，以及代码上做了哪些针对性优化。

### 结论：卡在链路，不在 CPU

同一个页面，本地 `localhost` 是毫秒级，经 ngrok 隧道要按秒算。实测差异：

| 指标 | 本地 | 经 ngrok |
|---|---|---|
| 单次请求耗时 | ~47 ms | 470~550 ms（首包 2 s 以上） |
| 有效下载速度 | 本地磁盘 | 47~54 KB/s（约 400 Kbps） |

也就是说，**每一次请求都要绕一圈公网**，带宽只有几十 KB/s。在这种链路上，
任何"多传一个字节"和"多一次往返"都会被放大成肉眼可见的等待。

### gzip 压缩

文本响应统一在 `HttpUtil.sendTextBody()` 出口压缩，`sendHtml` / `sendJs` / `sendCss` 都走这里。

- 只压 **HTML / CSS / JS**（文本，压缩率 70%~85%）
- 低于 1024 字节不压：gzip 头尾约 20 字节，小响应压完反而更大
- 只在请求头 `Accept-Encoding` 含 `gzip` 时压；压完不比原文小就发原文
- 响应必带 `Vary: Accept-Encoding`。同一 URL 会返回两种字节，不声明这一点，
  中间缓存可能把压缩体发给不支持的客户端
- 流量统计记的是**压缩后**的实际字节数，与实际带宽消耗一致

实测：

| 资源 | 原文 | gzip 后 | 减少 |
|---|---|---|---|
| `chat.html` | 116 KB | 32 KB | 72% |
| `app.css` | 32.0 KB | 8.7 KB | 73% |
| `app.js` | 16.0 KB | 5.7 KB | 64% |
| `theme.js` | 2.8 KB | 1.4 KB | 48% |
| `media.js` | 5.8 KB | 2.5 KB | 57% |
| **聊天室首屏合计** | **169 KB** | **50 KB** | **70%** |

按 48 KB/s 估算：`169 / 48 ≈ 3.5 s` → `50 / 48 ≈ 1.0 s`。

### 缓存策略

| 响应类型 | `Cache-Control` | 理由 |
|---|---|---|
| HTML 页面 | `no-store, no-cache, must-revalidate` | 页面含当前用户的昵称/头像/配色覆盖，缓存会串号或"设置改了页面没变" |
| CSS / JS **带 `?v=`** | `public, max-age=31536000, immutable` | 引用里带了版本号，说明这份字节是确定的，改文件必然改版本号 |
| CSS / JS **不带 `?v=`** | `no-cache` | 允许存副本但每次回源确认，避免直接敲 `/app.css` 时拿到旧内容 |
| 媒体（`/image/<id>`） | `public, max-age=86400` | 媒体 id 是内容标识，同一个 id 不会变内容。下载类请求（`download=1` / 文件）不加缓存 |
| JSON 接口 | `no-store` | 含会话与用户数据 |

效果：第二次进站时四个静态资源全部命中本地缓存，**一个字节都不传**，
只剩 HTML（约 32 KB 压缩后）需要下载。

> **改静态资源的铁律**：改完 `app.css` / `app.js` / `theme.js` / `media.js`，
> 必须把 HTML 里所有引用上的 `?v=` 版本号递增（如 `20261123d` → `20261123e`）。
> 否则浏览器认的是旧地址，会拿着旧文件跑到一年后。

### 改动生效路径

| 改了什么 | 需要做什么 |
|---|---|
| `web/*.css`、`*.js` | 递增 `?v=` 版本号 → 刷新页面 |
| `web/*.html` | **重启 JVM**。页面模板在 `WebPages` 里是 `static final`，类加载时读一次就常驻，改文件不重启看不到 |
| Java 代码 | 重新编译 + 重启 |

`WebResources.load()` 每次请求都读文件，不缓存，所以 CSS/JS 改完不用重启；
HTML 走的是 `WebPages` 的静态常量，必须重启。

### 有意没做的

| 项 | 原因 |
|---|---|
| 屏幕共享推流压缩 | 帧本来就是 JPEG，再压几乎没收益。真正的瓶颈是物理带宽：5 fps × 每帧 40 KB ≈ 200 KB/s，而隧道只有 48 KB/s，差 4 倍。只能靠降帧率/降质量或换链路解决 |
| 数据库连接池 | 56 处 `getConnection()` 每次新建。SQLite 本地开销是毫秒级，只在网络盘上才会明显；加池会引入连接状态管理成本，当前并发量不值得 |
| 静态资源 ETag / 304 | 已有 `?v=` 强缓存，304 协商反而多一次往返 |
| 拆分 `chat.html` | 单文件 116 KB 含内联样式与脚本。拆分能进一步减小首屏，但会打散现在"一页一看"的可读性，先靠 gzip 抵消 |

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
- 请求体上限：普通 64KB，上传 25MB，屏幕共享单帧 2MB
  - 关键点：限制在**流式读取时按实际字节数强制生效**，不依赖 `Content-Length`；否则用 `Transfer-Encoding: chunked` 不带长度头即可绕过
- 管理员登录锁定：失败 3 次锁 10 分钟
- 注册频率限制：每 IP 每小时 5 次
- SSE 连接数限制：每 IP 10、全局 500，并有空闲超时回收
- 单文件大小按用途限制；每用户每用途的条数与字节上限
- 限流器计数表有上限，防内存膨胀

**已知弱点**（未修，按需处理）

- 门禁码与默认管理员密码为明文配置，默认值可直接跑起来
- Session token 走 URL query 传递（同一来源内，`Referrer-Policy: same-origin` 限制外泄）
- PBKDF2 迭代 10000 次，低于现代推荐值
- `data/chat.db` 未加密，落盘可见

---

## 配置项

把 `config.properties.example` 复制为 `config.properties` 再改。**不建这个文件也能跑**，下表就是全部默认值。

配置在类加载时读一次（`Config` 静态块），**改完必须重启**。

### 服务与站点

| 键 | 默认 | 说明 |
|---|---|---|
| `port` | 8443 | 监听端口 |
| `site.title` | 519SB-Chat | 页面标题 |
| `keystore.path` | keystore.p12 | 证书文件 |
| `keystore.password` | changeit | 证书密码 |
| `db.file` | data/chat.db | SQLite 路径（父目录不存在会自动创建） |
| `max.history` | 200 | 聊天记录保留条数，超出后旧消息被裁掉（媒体不跟着删） |
| `thread.pool.size` | 16 | **已废弃**：现用虚拟线程（`newVirtualThreadPerTaskExecutor`），这个值不再生效，留着只为兼容旧配置 |
| `auto.open.browser` | true | 启动后自动打开浏览器 |
| `auto.open.path` | /login | 自动打开的目标路径，可改 `/chat` 或 `/posts` |

### 账号与门禁

| 键 | 默认 | 说明 |
|---|---|---|
| `access.code` | 519666233 | 门禁验证码，通过后 30 天免输（Cookie `gate_ok`） |
| `admin.username` | admin | 默认管理员账号 |
| `admin.password` | admin123 | 默认管理员密码 |
| `session.ttl.days` | 30 | 会话有效期 |

> `admin.username` / `admin.password` **只在首次建库时生效**。库里已经有这个账号后，
> 改配置不会改密码——要改请走个人中心或管理后台。

### 限额与限流

| 键 | 默认 | 说明 |
|---|---|---|
| `login.max.attempts` / `login.window.ms` | 5 / 60000 | 登录失败 X 次 / Y 毫秒内 |
| `send.max.count` / `send.window.ms` | 5 / 3000 | 发消息频率 |
| `max.image.size` | 5242880 | 图片类单文件上限（5MB） |
| `image.max.width` / `image.quality` | 800 / 0.6 | 前端压缩参数：最长边与 JPEG 质量 |
| `max.media.size.<用途>` | file 20MB，其余同上 | 按用途覆盖单文件上限 |
| `sse.idle.timeout.ms` | 600000 | SSE 空闲多久断开（10 分钟） |
| `trusted.proxies` | 127.0.0.1,::1 | 只有直连来自这些地址时才采信 `X-Forwarded-For` |

### 上传配额（每用户，按用途独立计数）

| 用途 | 条数 | 总字节 | 单文件 | 保留期 |
|---|---|---|---|---|
| `message` 聊天图 | 300 | 200MB | 5MB | 永久 |
| `avatar` 头像 | 10 | 5MB | 5MB | 永久（只留最近 5 张） |
| `post` 帖子图 | 300 | 200MB | 5MB | 永久 |
| `comment` 评论图 | 200 | 150MB | 5MB | 永久 |
| `voice` 语音 | 100 | 100MB | 5MB | 永久 |
| `anim` 动图 | 100 | 100MB | 5MB | 永久 |
| `file` 文件 | 100 | 500MB | 20MB | 永久 |

配置键：`max.image.count.per.user.<用途>`、`max.image.bytes.per.user.<用途>`。
不带用途后缀的旧键（`max.image.count.per.user`）**只对 message / post 生效**，这是历史行为。

**保留期默认全部永久**：`Config.imageRetentionMs()` 返回 0 表示不清理。要控制体积就配正数（毫秒）：

```properties
# 聊天图保留 30 天，语音保留 7 天
image.retention.ms.message=2592000000
image.retention.ms.voice=604800000
```

> 改成有限保留期前想清楚：**消息记录不会跟着删**，图片过了保留期被清理后，
> 那条消息就变成裂图（`/image/<id>` 返回 404）。
> 后台的「清空消息」也只删 `messages`，不动 `images`。

### 屏幕共享参数

`screen.fps`、`screen.max.width`、`screen.quality`、`screen.max.viewers`、
`screen.idle.timeout.ms`、`screen.max.frame.bytes`、`screen.viewer.timeout.ms`，
含义见[屏幕共享](#屏幕共享)。这些值都做了范围钳制，写错或越界只会退回默认值，不会让服务起不来。


---

## 运行环境

| 项目 | 要求 | 说明 |
|---|---|---|
| JDK | **21+** | 必须。用了虚拟线程与文本块，21 以下直接编不过 |
| 依赖 jar | 3 个 | `sqlite-jdbc` 3.45.3.0、`slf4j-api` 2.0.12、`slf4j-simple` 2.0.12（见 `pom.xml`）。仓库里不带 jar，由 Maven 管理 |
| 操作系统 | 全平台 | `start.bat` / `setup.bat` 只适用于 Windows；Linux / macOS 按[方式 C](#方式-c手动-javaclinux--macos--不装-maven)手动编译 |
| 端口 | 8443 | 只监听 `127.0.0.1`。防火墙不用对外放行，外网访问一律走 ngrok |
| 磁盘 | 按需 | 媒体全部以 BLOB 存在 `data/chat.db`，图片改为永久保留后体积会持续增长 |

两条容易被误判为故障的正常现象：

- 启动时会打印 `SLF4J(W): No SLF4J providers were found`。这不影响运行，日志本来就走 `System.out`。
- 浏览器提示证书不受信任。自签名证书必然如此，点「高级」→「继续访问」，或改用 ngrok 域名。

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
不用编译，但要**重启服务**。模板是运行时读取的，但在 `WebPages` 里以 `static final` 常量持有，类加载时读一次就常驻内存，只刷新页面看不到变化。CSS/JS 则不用重启，改完递增 `?v=` 版本号即可。

**本地很快，经 ngrok 特别卡**
不是代码慢，是链路慢。实测隧道单次请求 470~550 ms、带宽 47~54 KB/s，而本地 `localhost` 是 47 ms、带宽无上限。已经做了 gzip（首屏 169 KB → 50 KB）和静态资源强缓存（第二次访问零传输），剩下的瓶颈是隧道带宽本身，换国内小服务器或局域网直连才能根治。详见[传输与缓存](#传输与缓存)。

**聊天里图片是一堆裂图**
`/image/<id>` 返回 404，说明 `images` 表里没有这条 BLOB。常见原因：老版本默认把聊天图保留 24 小时、语音 7 天，过期就删了（消息记录不删），现在默认已改为永久保留，但**已被删的恢复不了**。也检查一下是不是拿了一份不完整的库在跑。

**启动报 `Address already in use` / 端口被占用**
上一个 Java 进程没退干净：`pkill -f BootstrapLayer.ChatServer`（Windows 用任务管理器结束 java.exe），或把 `port` 改成别的值。

**登录后马上又被弹回登录页**
通常是 token 已失效（改过密码、被新设备顶掉、超过 `session.ttl.days`）。这个项目是**单设备登录**：同一账号在新设备登录会让旧设备下线，这是设计行为。清掉站点数据重新登录即可。

**改了 CSS，刷新还是旧样式**
两件事都检查：① HTML 里引用的 `?v=` 版本号有没有递增；② 现在带版本号的资源是 `immutable` 强缓存，浏览器一年内不会回源，只能靠改版本号强制更新。

**屏幕共享一开就卡成幻灯片**
预期之内。15 fps、每帧 30~60 KB 需要 0.5~1 MB/s，而隧道只有 ~50 KB/s。把 `screen.fps` 调到 2~5、`screen.max.width` 调到 960 以下，或改用局域网访问。

**数据库和图片存在哪，怎么备份**
全部在 `data/chat.db` 一个文件里（图片也是，存成 BLOB）。备份用 `sqlite3 data/chat.db ".backup 备份名.db"`，细节见[数据与备份](#数据与备份)。

---

## 开发提示

- **改前端**：直接编辑 `src/main/resources/web/` 下的文件，不用编译。改 `app.css` / `app.js` / `theme.js` / `media.js` 后**必须递增各页面引用上的 `?v=` 版本号**，否则用户拿到的是强缓存里的旧文件（详见「传输与缓存」）。
- **改页面模板**：`*.html` 改完要重启服务——`WebPages` 里的常量是类加载时读取的。
- **改 Java**：重新 `javac` 到 `out/` 或让 IDEA 编译，然后重启。
- **加数据库列**：`ALTER TABLE` 必须放在该表的 `CREATE TABLE` **之后**。放在前面时新库里表还不存在，ALTER 会失败并被静默忽略，导致全新部署缺列——这个坑踩过。
- **加 SSE 事件类型**：`chat.html` 的 `onmessage` 末尾有 `appendMessage` 兜底分支，未知事件会被当成聊天消息渲染。**新增事件必须在兜底之前显式忽略或处理**。
- **加页面**：模板放 `src/main/resources/web/`，视图常量用 `WebResources.load("xxx.html")`，处理器里用 `HttpUtil.sendHtmlThemed(...)` 传用户配色。
- **插入用户数据**：服务端拼 HTML 必须过 `HttpUtil.escapeHtml`，拼 JSON 用过 `escapeJson`（两者不等价）。前端一律用 `textContent` / DOM API，不用 `innerHTML` 拼用户内容。
- **新增静态资源**：改完必须递增所有页面引用上的 `?v=` 版本号，否则用户永远拿旧文件（强缓存一年）。

---

## 踩过的坑

都是实际踩到过的，改代码前扫一眼能省几小时。

| 现象 | 真正的原因 | 正确做法 |
|---|---|---|
| `javac` 报「未闭合字符串文字」 | 页面模板作为 class 常量超过 **65535 字节** 上限，报错信息完全误导 | 模板一律放 `src/main/resources/web/`，不要再塞回 Java 常量 |
| 改了 HTML 刷新没变化 | `WebPages` 里的模板是 `static final`，类加载时读一次就常驻 | 改 `*.html` **必须重启 JVM**；CSS/JS 不用重启，但要加强制版本号 |
| 改了 CSS 用户看不到 | 带 `?v=` 的资源是 `immutable` 强缓存，一年不回源 | 每次改完递增版本号，这是唯一的失效手段 |
| 新库缺列 / `ALTER TABLE` 没生效 | 建表语句排在 `ALTER` 之后，新库里表还不存在，ALTER 失败被静默忽略 | `ALTER TABLE` 必须写在该表 `CREATE TABLE` **之后** |
| 新增 SSE 事件被当成聊天消息渲染 | `chat.html` 的 `onmessage` 末尾有 `appendMessage` 兜底分支，未知事件全走它 | 新事件在兜底之前显式 `return` 或处理 |
| 语音播放控制台报 CSP 违规 | CSP 是 `media-src 'self'`，`blob:` / `data:` 地址被拦 | 音频直接用 `/image/<id>` 原地址，不要 `createObjectURL` |
| 语音按钮变成一串 `<svg …>` 文字 | 用 `textContent` 写 SVG 标签，浏览器当纯文本显示 | 写标签结构用 `innerHTML`，写纯文本才用 `textContent` |
| 2.1 秒的语音显示 `0:00` | 两个 `fmtDuration` 重名，函数声明提升让后一个覆盖前一个，单位从毫秒变成秒 | 同名函数改造时换名（如 `fmtVoiceDuration`），别指望靠参数区分 |
| 打开 `/admin?token=…` 被踢回登录页 | head 里的守卫脚本先判断 localStorage 有没有 token，没先把这个 query 参数收进去 | 守卫里先 `localStorage.setItem` 收 token，再判断 |
| `/screen` 帧提交被 429 | 通用限流是每分钟 300 次，而推帧是每秒十几次，窗口瞬间打满 | 屏幕共享单独走 `wrapScreen`，不套通用限流 |
| 想加"图片存到 uploads 目录" | 现有实现全部存 SQLite BLOB，没有文件目录 | 别改存储方式，否则旧数据和备份策略全部失效 |
| 后台清空消息后磁盘没变小 | 「清空消息」只删 `messages`，`images` 不动 | 要回收空间得单独清 `images`，并接受对应消息变裂图 |

