# 519SB - 宿舍聊天室

基于 Java 原生 HTTPS + SSE 的简易聊天室，支持文字和图片。外网访问统一通过 ngrok 转发，无需关心本机 IP。

## 三分钟快速上手

1. 装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）1.安装 JDK 17+（https://adoptium.net/）
2. 双击 `setup.bat` 生成证书
3. 用 IDEA 打开项目，运行 `BootstrapLayer/ChatServer.java`
4. 另开命令行运行 `ngrok http https://localhost:8443`
5. 把 ngrok 给的 `https://xxx.ngrok-free.app` 发给朋友

**不用改任何配置，默认就能跑。**

---

## 功能特性

- 文字消息实时推送（SSE）
- 图片发送（前端自动压缩，最大宽度 800px，JPEG 质量 0.6）
- 聊天历史记录（内存中保留最近 200 条）
- HTTPS 加密传输（自签名证书）
- 通过 ngrok 支持外网访问
- 配置与代码分离，迁移只需 3 步
- 服务器只监听 `127.0.0.1`，外部流量一律走 ngrok，安全性好

---

## 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17 或更高 | 需要 `javac`、`java`、`keytool` |
| ngrok | 最新版 | 外网访问必需 |
| IntelliJ IDEA | 可选 | 用命令行也能运行 |

检查 JDK 是否安装成功，打开命令行输入：

```bash
java -version
javac -version
keytool -help
```

三个命令都能正常输出版本号即可。

---

## 一、克隆项目

```bash
git clone https://github.com/AngelicaeTao/519SB.git
cd 519SB
```

---

## 二、生成证书

在项目根目录**双击 `setup.bat`**，几秒后会在当前目录生成 `keystore.p12`。

如果 Windows 弹「已保护你的电脑」，点击「更多信息」→「仍要运行」。

如果 `setup.bat` 一闪而过看不到报错，右键它 →「以管理员身份运行」，或者从命令行执行 `setup.bat` 查看具体错误。

---

## 三、启动 Java 服务器

### 方式 1：IDEA 中运行（推荐）

1. 用 IntelliJ IDEA 打开项目文件夹。
2. 找到 `BootstrapLayer/ChatServer.java`。
3. 点击绿色三角运行。

### 方式 2：命令行运行

在项目根目录打开命令行，输入：

```bash
javac -encoding UTF-8 -d out BootstrapLayer\ChatServer.java ConfigurationLayer\Config.java DataLayer\MessageStore.java HandlerLayer\ChatHandler.java HandlerLayer\ImageHandler.java InfrastructureLayer\SseManager.java ViewLayer\WebPages.java

java -cp out BootstrapLayer.ChatServer
```

控制台看到以下内容即成功：

```
==========================================
  宿舍聊天室 服务器已启动
==========================================
  本机测试:  https://localhost:8443/

  外网访问请另开一个命令行窗口，运行：
    ngrok http https://localhost:8443
  然后把 ngrok 给出的地址发给朋友。
==========================================
```

### 本机测试

浏览器打开 `https://localhost:8443/`，会提示证书警告，点击「高级」→「继续访问」即可。

---

## 四、配置 ngrok（外网访问）

### 第 1 步：下载 ngrok

1. 打开浏览器访问：https://ngrok.com/download
2. 页面往下找 **Windows** 那一栏
3. 点击 **Download for Windows** 按钮
4. 下载下来是一个 `ngrok-v3-stable-windows-amd64.zip` 压缩包
5. **解压这个 zip**，里面只有一个 `ngrok.exe` 文件

### 第 2 步：把 ngrok.exe 放到一个好找的地方

建议放在项目根目录（和 `setup.bat` 同一层），或者放到 `C:\ngrok\` 目录里。

假设你放在了：

```
C:\Users\你的用户名\IdeaProjects\untitled1\ngrok.exe
```

### 第 3 步：注册并获取 Token

1. 浏览器打开 https://dashboard.ngrok.com/signup
2. 用邮箱注册一个账号（免费版就够）
3. 注册完会自动跳转到后台
4. 左侧菜单点 **Your Authtoken**，或直接访问 https://dashboard.ngrok.com/get-started/your-authtoken
5. 页面上会显示一串很长的字符，形如：

```
2bX6mK9pQzRw1v3y8n4j5l6k7h8g_5xYzAbCdEfGhIjKlMnOpQr
```

6. **点击右边的 Copy 按钮复制这一长串**

### 第 4 步：配置 Token（只做一次）

**关键：命令行要进入 ngrok.exe 所在的目录。**

1. 打开 ngrok.exe 所在的文件夹（比如 `untitled1` 或 `C:\ngrok\`）
2. 点击文件夹地址栏，输入 `cmd`，按回车
   （或者按住 Shift + 右键空白处 →「在此处打开 PowerShell/终端」）
3. 弹出的命令行窗口里，输入下面这行（**把 `你的Token` 换成刚才复制的那串字符**）：

```bash
ngrok config add-authtoken 你的Token
```

4. 按回车
5. 看到这样一行就成功了：

```
Authtoken saved to configuration file: C:\Users\你的用户名\AppData\Local\ngrok\ngrok.yml
```

> 这一步**只需要做一次**。以后启动 ngrok 就不用再配置了。

### 第 5 步：确认 Java 服务器已启动

在启动 ngrok 之前，**必须保证 Java 服务器已经在运行**。

- 如果你在 IDEA 里已经点了运行 `ChatServer.java`，控制台显示「宿舍聊天室 服务器已启动」，说明服务器在运行。
- 如果你还没有启动，先启动它，然后再进入下一步。

### 第 6 步：启动 ngrok 隧道

**不要关掉 Java 服务器**，打开**另一个**命令行窗口（和刚才配置 Token 用的是同一个目录，也就是 ngrok.exe 所在目录）。

在这个新窗口里输入：

```bash
ngrok http https://localhost:8443
```

按回车。

会出现一个界面，类似这样：

```
ngrok                                              (Ctrl+C to quit)

Session Status                online
Account                       你的邮箱 (Plan: Free)
Version                       3.x.x
Region                        Japan (jp)
Latency                       348ms
Web Interface                 http://127.0.0.1:4040
Forwarding                    https://xxxx-xxxx-xxxx.ngrok-free.app -> https://localhost:8443

Connections                   ttl     opn     rt1     rt5     p50     p90
                              0       0       0.00    0.00    0.00    0.00
```

**关键信息就是 `Forwarding` 那一行**：

```
Forwarding  https://xxxx-xxxx-xxxx.ngrok-free.app -> https://localhost:8443
```

这个 `https://xxxx-xxxx-xxxx.ngrok-free.app` 就是外网访问地址，复制下来。

### 第 7 步：发给朋友

把地址（`https://xxxx-xxxx-xxxx.ngrok-free.app`）发给舍友。

舍友用浏览器打开就能进聊天室。第一次会弹证书警告，点击「高级」→「继续前往」。

---

## 五、ngrok 命令速查

```bash
# 配置 Token（只做一次）
ngrok config add-authtoken 你的Token

# 启动隧道（每次都要做）
ngrok http https://localhost:8443

# 带地区参数启动（降低延迟）
ngrok http https://localhost:8443 --region ap

# 关闭 ngrok
Ctrl + C
```

`--region` 可选值：`us`（美国）、`eu`（欧洲）、`ap`（亚太）、`au`（澳洲）、`sa`（南美）、`jp`（日本）、`in`（印度）。亚洲用户一般用 `ap` 或 `jp` 延迟较低。

---

## 六、项目结构

```
519SB/
├── config.properties.example    配置模板（复制为 config.properties 后可用）
├── keystore.p12                 HTTPS 证书（运行 setup.bat 生成，不提交）
├── setup.bat                    Windows 一键生成证书
├── README.md                    本说明
├── .gitignore
├── BootstrapLayer/
│   └── ChatServer.java          程序入口
├── ConfigurationLayer/
│   └── Config.java              读取配置
├── DataLayer/
│   └── MessageStore.java        历史消息
├── HandlerLayer/
│   ├── ChatHandler.java         聊天逻辑
│   └── ImageHandler.java        图片逻辑
├── InfrastructureLayer/
│   └── SseManager.java          SSE 连接管理
└── ViewLayer/
    └── WebPages.java            前端页面
```

---

## 七、配置说明

如果不想用默认配置，把 `config.properties.example` 复制为 `config.properties`，然后修改里面的值。

**保存时务必选择 UTF-8 编码**，否则中文会乱码。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `port` | 8443 | 服务器端口 |
| `site.title` | 宿舍聊天室 | 网站标题 |
| `keystore.path` | keystore.p12 | 证书路径 |
| `keystore.password` | changeit | 证书密码 |
| `max.history` | 200 | 历史消息条数上限 |
| `max.image.size` | 5242880 | 单张图片最大字节数（5MB） |
| `image.max.width` | 800 | 前端压缩图片最大宽度 |
| `image.quality` | 0.6 | 前端压缩 JPEG 质量 |
| `thread.pool.size` | 16 | HTTP 线程池大小 |

---

## 八、迁移到新电脑

1. 拷贝整个项目文件夹（**不要**拷贝 `keystore.p12`、`out`、`.idea`）。
2. 在新电脑安装 JDK 17+。
3. 双击 `setup.bat` 生成新证书。
4. 启动 `ChatServer`。
5. 配置 ngrok 并启动隧道（如果新电脑没配过 Token，需要重新 `ngrok config add-authtoken`）。
6. 把 ngrok 地址发给舍友。

---

## 九、常见问题

### Q1：浏览器提示「你的连接不是私密连接」？

这是自签名证书的正常现象，点击「高级」→「继续访问」。

### Q2：ngrok 免费版地址每次重启都变？

是的，免费版每次重启会分配新地址。建议保持 ngrok 长时间运行。想要固定域名需要付费。

### Q3：图片发不出去？

检查图片大小（压缩后应小于 5MB），或按 F12 查看浏览器控制台的报错。

### Q4：历史消息重启后消失？

历史存在内存中，重启清空。如需持久化，可以修改 `MessageStore` 存到文件。

### Q5：服务器打不开，控制台报「找不到 keystore」？

双击 `setup.bat` 生成证书，然后重启服务器。

### Q6：setup.bat 闪退？

右键 `setup.bat` →「以管理员身份运行」，或者从命令行执行 `setup.bat` 查看具体错误。

### Q7：命令行提示 `'ngrok' 不是内部或外部命令`？

原因：你没有在 ngrok.exe 所在的目录运行命令。

解决：
- 检查你当前命令行所在路径是不是 ngrok.exe 的位置（命令行里显示的前缀）。
- 或者输入完整路径：

```bash
C:\ngrok\ngrok.exe http https://localhost:8443
```

### Q8：ngrok 提示 `authentication failed: The authtoken you specified does not look like a proper ngrok authtoken`？

原因：你复制 Token 时复制错了，或者没替换掉 `你的Token` 这几个字。

解决：回到「第 3 步」，重新复制一遍 Token，粘贴时不要带任何多余字符。

### Q9：ngrok 启动了，但朋友打开显示 `ERR_NGROK_8012` 或 502？

原因：Java 服务器没启动，或者端口对不上。

解决：
1. 确认 `ChatServer.java` 正在运行。
2. 确认 ngrok 命令里写的端口和 `config.properties` 里的 `port` 一致（默认都是 8443）。
3. 用浏览器打开 `https://localhost:8443/` 看能不能访问（要先跳过证书警告）。

### Q10：ngrok 显示的延迟很高（比如 348ms）？

这是正常的，因为你连的是日本节点。免费版可以手动换地区：

```bash
ngrok http https://localhost:8443 --region ap
```

### Q11：怎么关闭 ngrok？

在 ngrok 那个命令行窗口里按 **Ctrl + C**。

关掉后外网就访问不了了，但本机 `https://localhost:8443/` 仍然可用（只要 Java 服务器还在跑）。

---

## 十、安全提醒

1. `keystore.p12` 包含私钥，**不要提交到公开仓库**，`.gitignore` 已经忽略。
2. 使用 ngrok 时，任何拿到地址的人都能访问你的聊天室，演示完毕后请**按 Ctrl + C 关闭服务器和 ngrok**。
3. 服务器只监听 `127.0.0.1`，本机以外的设备无法直接访问，必须通过 ngrok，安全性更好。
4. 校园网可能禁止私设服务器和内网穿透，请先确认校规。

---

## 十一、技术栈

- Java 原生 `com.sun.net.httpserver.HttpsServer`
- SSE（Server-Sent Events）实时推送-SSE（服务器发送事件）实时推送
- 前端原生 HTML/CSS/JavaScript
- Canvas 图片压缩
- ngrok 内网穿透

---

## 许可证

仅供学习交流使用。
