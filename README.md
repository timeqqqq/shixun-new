# 校园生活百事通（智能问答知识库）后端

## 1. 项目简介
本项目用于集中管理校园生活常见问答信息，支持搜索、Excel 导入、网站爬取、用户贡献、管理员审核、热点推荐等功能。  
后端技术栈：Spring Boot 3.2 + JDK 17 + Maven + MyBatis-Plus + MySQL 8.0 + Redis。

## 2. 当前已实现功能
- 健康检查：`GET /api/ping`
- 提问检索：`GET /api/search?q=关键词`（Top-5、Redis 缓存 5 分钟、异步记录 query_log）
- Excel 导入：`POST /api/admin/import/excel`
- 模板下载：`GET /api/admin/import/template`
- 网站爬取：`POST /api/admin/crawl/start`、`GET /api/admin/crawl/tasks`
- 用户贡献：`POST /api/contributions`、`GET /api/contributions/mine`
- 审核管理：分页查询、通过、拒绝、编辑后通过、批量拒绝
- 热点推荐：`GET /api/hot?period=week|month|all`、`POST /api/admin/hot/pin/{questionId}`
- 并发优化：IP 限流（30 次/分钟）、缓存统计、异步日志

## 3. 环境要求
- JDK 17
- Maven 3.9+
- MySQL 8.0（默认端口 `3306`）
- Redis（本项目使用端口 `6380`）

## 4. 本地启动流程（答辩演示推荐）
1. 启动 MySQL 和 Redis。  
2. 启动后端：

```bat
cd /d e:\vs2022练习\2\vscode-java\first\shixun-new
mvn spring-boot:run
```

3. 验证接口：
- `http://localhost:8081/api/ping`

## 5. 常用测试命令（cmd）
```bat
curl "http://localhost:8081/api/ping"
curl "http://localhost:8081/api/search?q=食堂"
curl "http://localhost:8081/api/hot?period=week"
curl "http://localhost:8081/api/admin/cache/stats"
```

## 6. 编码与 BOM 防踩坑（重要）
你之前遇到的 `未结束的字符串文字`、`非法字符 '\ufeff'`，本质是**文件编码被破坏**或**UTF-8 BOM** 导致。

### 6.1 VS Code 设置（建议固定）
`.vscode/settings.json` 建议包含：

```json
{
  "files.encoding": "utf8",
  "files.autoGuessEncoding": false
}
```

### 6.2 终端建议
- 前端 `npm` 命令优先在 `cmd` 中执行（避免 PowerShell 执行策略拦截）。
- 后端 Java 文件避免用会自动加 BOM 的外部工具批量改写。

### 6.3 编译前快速自检
```bat
cd /d e:\vs2022练习\2\vscode-java\first\shixun-new
mvn -DskipTests compile
```

- 若 `BUILD SUCCESS`，说明后端源码编码与语法正常。

### 6.4 一键去除 Java 文件 BOM（PowerShell）
如果再次出现 `非法字符 '\ufeff'`，可在后端目录执行：

```powershell
$enc = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem -Recurse -Filter *.java | ForEach-Object {
  $text = [System.IO.File]::ReadAllText($_.FullName)
  [System.IO.File]::WriteAllText($_.FullName, $text, $enc)
}
```

然后重新编译：

```bat
mvn -DskipTests compile
```

## 7. JMeter 压测
- 文件：`test/jmeter/campus-qa-search-50x10.jmx`
- 线程数：50
- 循环数：10

## 8. 后续建议
- 前后端联调时，先确认后端 `8081` 与前端 `5173` 都在监听。
- 答辩当天按“启动流程 + 接口验证 + 功能演示路径”进行，稳定性最高。

## 9. 可配置中文敏感词库
- 配置位置：pplication.yml -> pp.audit.sensitive-words
- 格式：逗号分隔，例如：诈骗,色情,赌博,毒品,暴力,恐怖,辱骂
- 生效方式：修改后重启后端。

