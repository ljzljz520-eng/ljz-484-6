# 研究笔记发布项目（research-notes）

一个可以**直接运行**的研究笔记发布小系统：

- 研究员在后台维护 **课题、笔记、章节、引用说明**，并用一个开关控制 **是否公开**；
- 前端提供 **公开目录页** 和 **笔记阅读页**（含章节导航、正文、引用列表）；
- 后端是**纯 Java 零依赖**的 HTTP 服务（JDK 自带 `com.sun.net.httpserver`，自带极简 JSON 库），
  数据读写在本地 **`data/db.json`** 一个文件里；
- **私密保证：私密笔记绝不会出现在公开目录中，直接拼 URL 访问阅读接口也会返回 404**（不泄露笔记是否存在）。

> 适合个人/课题组在本机或内网使用。后台没有登录鉴权，请勿直接暴露到公网。

---

## 1. 环境要求

只需要 **JDK 8 或更高版本**（JDK 8 / 11 / 17 / 21 均可），不需要 Maven、Gradle，也不需要联网下载依赖。

检查是否已安装：

```bash
java -version
javac -version
```

如果没有 JDK：

- Windows / macOS：从 <https://adoptium.net> 下载 Temurin JDK 17 安装包；
- Debian/Ubuntu：`sudo apt-get install -y default-jdk-headless`；
- 没有 root 权限时，可下载免安装版（tar.gz）解压后，把 `JAVA_HOME` 指到解压目录，见文末“常见问题”。

---

## 2. 一分钟启动

### Linux / macOS

```bash
./scripts/run.sh
```

脚本会自动编译并启动，看到下面的输出即成功：

```
========================================================
 研究笔记发布服务已启动
 公开目录 : http://localhost:8080/
 研究员台 : http://localhost:8080/admin
 数据文件 : /.../data/db.json
 停止服务 : 按 Ctrl+C
========================================================
```

### Windows

```bat
scripts\compile.bat
scripts\run.bat
```

### 打开页面

| 页面 | 地址 | 说明 |
| --- | --- | --- |
| 公开目录 | <http://localhost:8080/> | 任何人可见，只列出公开笔记 |
| 阅读页示例 | <http://localhost:8080/note/n1> | 单篇公开笔记的阅读页 |
| 研究员后台 | <http://localhost:8080/admin> | 维护课题/笔记/章节/引用/公开开关 |

首次启动若 `data/db.json` 不存在，会自动生成带 2 个课题、5 篇笔记（其中 2 篇私密）的示例数据。

换端口 / 数据目录 / 前端目录：

```bash
./scripts/run.sh 9000                 # 只用 9000 端口
./scripts/run.sh 8080 ./mydata ./web  # 自定义数据目录和前端目录
```

---

## 3. 目录结构

```
research-notes/
├── README.md                 # 本文件
├── scripts/
│   ├── compile.sh / run.sh   # Linux/macOS 编译、启动
│   ├── compile.bat / run.bat # Windows 编译、启动
│   └── smoke.sh              # 私密隔离冒烟测试
├── src/com/researchnotes/
│   ├── ResearchServer.java   # 入口：启动 HTTP 服务（默认 8080）
│   ├── HttpHandlers.java     # 路由：/api/** JSON 接口 + web/ 静态资源
│   ├── DataStore.java        # 本地数据读写、校验、公开过滤、示例数据
│   └── Json.java             # 零依赖 JSON 解析与序列化
├── web/                      # 纯静态前端（原生 HTML/CSS/JS，无需打包）
│   ├── index.html / index.js     # 公开目录页
│   ├── reader.html / reader.js   # 公开阅读页
│   ├── admin.html / admin.js     # 研究员后台
│   ├── common.js                 # API 封装与安全 Markdown 渲染
│   ├── styles.css
│   └── favicon.svg
├── data/
│   └── db.json               # 首次启动自动生成；全部数据都在这一个文件
└── bin/                      # 编译产物（git 忽略）
```

---

## 4. 研究员操作指南（后台 `/admin`）

1. 打开 <http://localhost:8080/admin>。
2. **课题管理**：新建课题（标题 + 简介）；课题下仍有笔记时删除会被拒绝，避免孤儿数据。
3. **笔记管理 → 新建笔记**：选择课题、填写标题、摘要、Markdown 正文后创建。
   **新建笔记默认是私密的**。
4. 在编辑页继续添加：
   - **笔记章节**：每节含标题、锚点（可留空自动生成）、Markdown 内容；
   - **引用说明**：类型（期刊/会议/教材/网页/数据集/其他）、标题、作者、年份、链接、说明；
   - 勾选 **“公开发布”** 并保存后，笔记才会出现在公开目录；随时取消勾选即可撤下。
5. 公开状态的笔记旁有 **“查看公开页”** 按钮，可预览读者看到的样子。

阅读页支持的 Markdown 语法：`#`~`####` 标题、`-`/`1.` 列表、`- [ ]` 任务项、
`>` 引用、`` `code` ``、围栏代码块、`**加粗**`、`*斜体*`、`[文字](链接)`、图片。
所有内容渲染前先做 HTML 转义，正文里的原始 HTML 不会被执行。

---

## 5. HTTP / JSON 接口

所有请求与响应均为 `application/json; charset=utf-8`。

### 公开接口（只返回公开数据）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/catalog` | 公开目录：按课题分组，只含公开笔记（无正文），没有公开笔记的课题不出现 |
| GET | `/api/notes/{id}` | 公开笔记详情（正文/章节/引用）；**私密或不存在一律 404** |
| GET | `/api/health` | 健康检查 |

示例：

```bash
curl http://localhost:8080/api/catalog
curl http://localhost:8080/api/notes/n1
curl -i http://localhost:8080/api/notes/n2   # 私密笔记 -> HTTP/1.1 404
```

### 研究员维护接口（`/api/admin/**`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/topics` | 课题列表（含笔记数/公开数） |
| POST | `/api/admin/topics` | 新建课题 `{title, description?}` |
| PUT | `/api/admin/topics/{id}` | 更新课题（可只传变更字段） |
| DELETE | `/api/admin/topics/{id}` | 删除空课题（非空返回 400） |
| GET | `/api/admin/notes` | 全部笔记（含私密，供后台使用） |
| POST | `/api/admin/notes` | 新建笔记，`isPublic` 强制按 `false` 处理 |
| PUT | `/api/admin/notes/{id}` | 更新笔记（章节、引用、`isPublic` 等） |
| DELETE | `/api/admin/notes/{id}` | 删除笔记 |
| GET | `/api/admin/notes/{id}` | 单篇笔记完整数据（含私密） |

新建/更新一篇公开笔记示例：

```bash
curl -X POST http://localhost:8080/api/admin/notes \
  -H 'Content-Type: application/json' -d '{
    "topicId": "t1",
    "title": "我的新笔记",
    "summary": "一篇通过接口创建的笔记",
    "content": "# 标题\n\n正文内容",
    "chapters": [
      {"heading": "引言", "body": "引言内容"}
    ],
    "references": [
      {"type": "期刊论文", "title": "某论文", "authors": "张三", "year": "2026",
       "url": "https://example.com/paper", "note": "重点参考第 3 节"}
    ]
  }'

# 拿到返回的 id（如 n6）后，再发布：
curl -X PUT http://localhost:8080/api/admin/notes/n6 \
  -H 'Content-Type: application/json' -d '{"isPublic": true}'
```

### 数据模型

```jsonc
// 课题
{ "id": "t1", "title": "...", "description": "...", "createdAt": "...", "updatedAt": "..." }

// 笔记（data/db.json 中的完整形态）
{
  "id": "n1",
  "topicId": "t1",
  "title": "...",
  "summary": "...",
  "content": "Markdown 正文",
  "isPublic": true,                 // false 时绝不会进入任何公开接口
  "chapters": [ {"heading": "...", "anchor": "chap-1", "body": "..."} ],
  "references": [ {"type": "期刊论文", "title": "...", "authors": "...",
                   "year": "2026", "url": "...", "note": "..."} ],
  "createdAt": "2026-08-28 09:00:00",
  "updatedAt": "2026-09-08 18:00:00"
}
```

---

## 6. 私密隔离是怎么保证的

1. **公开目录** `DataStore.publicCatalog()` 组装时逐篇判断 `isPublic == true`，
   私密笔记不会进入响应，连摘要和章节数都不返回；
2. **公开阅读接口** `DataStore.publicNote(id)` 对“不存在”和“存在但私密”返回完全相同的 **404**，
   无法通过遍历 ID 探测私密笔记；
3. 包含私密数据的接口全部在 `/api/admin/**` 下，公开前端页面从不请求它们；
4. **新建笔记默认私密**，必须研究员显式勾选公开并保存后才发布；
5. 取消公开开关是即时生效的：下一次请求公开接口即返回 404、目录中立即消失。

可用自带脚本一键验证（自动启停一个临时实例，不影响现有 `data/`）：

```bash
./scripts/smoke.sh 8099
# 期望最后输出：ALL SMOKE TESTS PASSED
```

---

## 7. 数据存储与备份

- 全部数据就是 **`data/db.json`**（UTF-8、带缩进的 JSON），可直接用编辑器查看、用 Git 版本管理。
- 每次保存采用“**写临时文件 + 原子替换**”，避免写入中断损坏数据。
- 备份只需复制 `data/db.json`；把它删掉后重启会重新生成示例数据。

---

## 8. 常见问题

**Q：只有免安装版 JDK（没有 root 权限安装）怎么办？**
下载解压后，用 `JAVA_HOME` 指定目录即可：

```bash
export JAVA_HOME=/path/to/jdk-17.x.x
$JAVA_HOME/bin/java -version    # 验证
./scripts/run.sh
```

**Q：端口被占用？**
`./scripts/run.sh 9090` 换一个端口。

**Q：如何只重新编译？**
`./scripts/compile.sh`（Windows 用 `scripts\compile.bat`）。

**Q：想清空示例数据重新开始？**
停止服务，删除 `data/db.json`，再启动即可重新生成。

**Q：中文乱码？**
服务端已固定使用 UTF-8 读写文件与响应；Windows 终端建议使用 Windows Terminal 或 PowerShell 7。

---

## 9. 后续可扩展方向（当前未实现）

- 后台登录/Token 鉴权（当前定位为本机/内网工具）；
- 全文检索、笔记标签；
- Markdown 图片上传；
- 数据从单文件迁移到 SQLite。
