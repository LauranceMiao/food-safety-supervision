# 校园食品安全监管系统

## 项目介绍

高校校内食堂的食品安全记录与学校监管平台。前端为 Vue 3 单页应用，后端为 Spring Boot 4 单体服务，使用 MySQL 8 保存业务数据；全部记录由人员通过网页录入，不接入温度传感器等外部设备。

项目围绕“采购需求与验收 → 食谱编排与供餐 → 检查与整改 → 反馈处理与统计”组织业务，供校内管理人员、食堂工作人员和师生按各自权限使用。工作人员健康档案、附件证据、操作审计和周期复盘贯穿相关流程。

### 目录结构

```text
.
├── backend/
│   ├── pom.xml                         # 后端依赖与打包配置
│   ├── config/                         # 本地数据库配置、账号文件及配置示例
│   ├── src/main/java/edu/campus/food/  # 认证、业务接口、算法与报表
│   ├── src/main/resources/
│   │   ├── application.yml             # 服务、数据库、上传与会话配置
│   │   └── db/migration/               # Flyway SQL 迁移
│   ├── src/test/                       # 后端测试
│   └── data/                           # 本地附件、日志与备份
├── frontend/
│   ├── package.json                    # 前端依赖与运行命令
│   ├── package-lock.json               # 前端依赖锁定文件
│   ├── vite.config.ts                  # 开发代理与构建配置
│   └── src/                           # 页面、路由、权限、状态与前端测试
├── scripts/                            # 本地接口检查和维护脚本（未公开发布）
├── 汇报材料/                           # 本地 PPT 与讲稿（不公开发布）
├── .presentation-build/                # 本地汇报材料生成脚本及中间产物（不公开发布）
├── README.md
└── LICENSE                             # MIT 许可证
```

`frontend/node_modules`、`frontend/dist` 和 `backend/target` 分别由依赖安装、前端构建和后端打包生成。`backend/config` 下除配置示例外的本地配置与账号文件，以及 `backend/data` 下的运行数据均已列入 `.gitignore`。

## 已实现的业务

- 九类权限身份：超级管理员、校长、食堂总管、监管人员、采购员、厨师、保洁员、其他工作人员、师生。采购员、厨师、保洁员分别使用独立角色 `PURCHASER`、`CHEF`、`CLEANER`；其他工作人员使用 `STAFF`，处理本人任务并填写、提交本人健康档案。师生自行注册，工作账号由超级管理员创建；服务端按角色与食堂范围校验权限，不能靠修改岗位名称取得业务权限。
- 三个初始食堂；支持供应商与证照、食材目录、采购批次、验收、菜品原料清单、每日食谱、实际批次领用与供餐、每日检查、现场检查项目、问题整改复核、师生反馈与食堂回复。新增批次的数字编号由系统自动生成。
- 总管按食堂、日期和餐次批量编排菜品、预计份数与制作要求，可修改草稿后发布。保存后的食谱草稿立即显示在总管列表；本食堂厨师只看到已发布的食谱，负责制作确认、实际批次用料、供餐和食品留样。菜品目录用于保存备选菜品，新增目录菜品后仍需加入每日食谱。供餐前必须确认制作并登记实际使用的合格食材批次。
- 总管创建所需食材、数量、日期和可选指定供应商的采购需求草稿，发布后本食堂采购员才能看到；采购员选择已发布需求登记到货并验收，厨师不承担采购验收。保洁员负责全部环境清洁与餐饮具、设备消毒。总管查看执行记录，负责计划、人员、每日管控检查和反馈整改。
- 支持本地附件上传、类型与大小校验、按权限下载。支持采购凭证、验收证据、现场问题证据、整改证据和反馈图片。
- 风险评分、食材批次双向追溯、风险驱动检查排程建议；排程由监管人员确认后才生成正式任务。
- 重大异常人工上报与处置时间线、操作审计、按日期和食堂筛选的统计及 CSV 导出。
- 工作人员自行填写本人基本信息、岗位、健康证明附件及培训记录，保存草稿后提交所属食堂总管审核。审核通过才进入“人员健康”正式名单；退回必须填写原因，工作人员修改后再次提交。校长、监管人员查看全校已批准档案，总管查看授权食堂的已批准档案及待审核记录。更新资料需要再次审核，期间原批准资料继续显示，提交和审核历史均保留。
- 留样、消毒、清洁记录；采购计划与实际到货、验收数量对照。
- 周排查和月调度草稿及人工确认、校长督办记录、证照临期和逾期事项等站内提醒。

业务边界见[当前边界](#当前边界)，操作流程见[使用方法](#使用方法)。系统中的演示记录均为模拟数据；风险分只用于内部排序，不代表事故概率或法定结论。

## 运行环境

| 部分 | 技术 |
|---|---|
| 后端 | JDK 17、Spring Boot 4.1.1、Spring Security、MyBatis、Flyway、Maven |
| 数据库 | MySQL 8.0，字符集 `utf8mb4` |
| 前端 | Node.js 24、Vue 3、TypeScript 5.9、Vite 8、Element Plus、ECharts |

本机演示环境已经建立 `campus_food_safety` 数据库和 `campus_app` 专用账号，连接信息保存在不纳入版本管理的 `backend/config/local-db.properties`。如果换一台电脑，请复制 `backend/config/local-db.properties.example`，填入新环境的连接信息。Flyway 在第一次启动时自动建表和初始化三个食堂及检查模板。

## 安装方法

以下命令使用 Windows PowerShell。先进入下载或克隆得到的项目根目录，再保存该目录路径：

```powershell
$projectRoot = (Get-Location).Path
```

后续安装和打包命令复用 `$projectRoot`；另开终端时，请先进入项目根目录。

### 1. 准备环境

安装上表中的 JDK 17、MySQL 8.0、Node.js 24 和 Maven，将相应命令加入 `PATH`，并启动 MySQL 服务。可用以下命令检查安装是否完成：

```powershell
java -version
mvn -version
node --version
npm.cmd --version
mysql --version
```

Python 3 仅用于运行本机保留的 `scripts` 检查、维护脚本（未纳入公开仓库），启动网页和后端服务不需要 Python。

### 2. 创建数据库和专用账号

若目标电脑尚未配置数据库，先以具有建库和授权权限的 MySQL 账号登录：

```powershell
mysql -u root -p
```

在 MySQL 提示符下执行以下 SQL，将 `REPLACE_WITH_YOUR_PASSWORD` 替换为自己的数据库密码：

```sql
CREATE DATABASE IF NOT EXISTS campus_food_safety
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'campus_app'@'localhost'
  IDENTIFIED BY 'REPLACE_WITH_YOUR_PASSWORD';
GRANT ALL PRIVILEGES ON campus_food_safety.* TO 'campus_app'@'localhost';
EXIT;
```

已有该账号时，`CREATE USER IF NOT EXISTS` 不会修改原密码，下一步应填写账号实际使用的密码。Flyway 负责创建业务表，安装时只需准备数据库与账号。

### 3. 配置数据库连接

在项目根目录执行，仅在配置文件不存在时复制示例：

```powershell
Set-Location $projectRoot
if (-not (Test-Path .\backend\config\local-db.properties)) {
    Copy-Item .\backend\config\local-db.properties.example .\backend\config\local-db.properties
}
notepad .\backend\config\local-db.properties
```

文件中的配置应与目标 MySQL 实例一致：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/campus_food_safety?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=campus_app
spring.datasource.password=REPLACE_WITH_YOUR_PASSWORD
```

### 4. 安装前端依赖并准备首次启动

项目包含 `package-lock.json`，可按锁定版本安装依赖：

```powershell
Set-Location (Join-Path $projectRoot 'frontend')
npm.cmd ci
Set-Location $projectRoot
```

随后按下节“打包运行”构建前端、打包并启动后端。若已执行 `npm.cmd ci`，可跳过下节的 `npm.cmd install`。

在空数据库首次启动前，可在启动后端的同一个 PowerShell 窗口中设置管理员初始密码，将下面的占位值替换为自己的密码：

```powershell
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = 'REPLACE_WITH_YOUR_ADMIN_PASSWORD'
```

只有数据库中没有任何用户时，启动代码才会创建 `admin`。未设置该变量时，系统生成 18 位随机初始密码，并在后端启动日志中输出；设置变量不会重置已有账号的密码。新数据库不会自动生成下文所列的本机演示账号，也不会自动写出本机的账号凭据文件；其他工作账号应在管理员页面创建，师生账号通过注册页面创建。

## 打包运行

在 PowerShell 中从项目根目录依次执行：

```powershell
$projectRoot = (Get-Location).Path
Set-Location (Join-Path $projectRoot 'frontend')
npm.cmd install
npm.cmd run build
Set-Location (Join-Path $projectRoot 'backend')
mvn -q package
java '-Dfile.encoding=UTF-8' -jar target\food-safety-0.1.0.jar
```

浏览器打开 `http://127.0.0.1:8080/#/login`。前端构建产物会由 Maven 打进后端 JAR，因此正式演示只需要启动 MySQL 和后端 JAR。请从项目的 `backend` 目录运行后端，使本地数据库配置和附件目录生效。

首次空数据库启动会创建 `admin` 管理员，密码使用安装时设置的 `APP_BOOTSTRAP_ADMIN_PASSWORD`，未设置时由系统随机生成并在启动日志中输出。已有本机管理员账号信息保存在 `backend/config/admin-credentials.properties`，演示账号列表保存在 `backend/config/demo-accounts.properties`；这两个文件均已加入 `.gitignore`，不随公开源码发布。下表仅说明本机模拟账号的角色用途，新安装环境不会自动创建这些工作和师生账号。登录后可通过右上角“修改密码”设置独立密码。

| 登录账号 | 页面显示名称 | 身份与演示用途 |
|---|---|---|
| `admin` | 超级管理员 | 账号和基础配置 |
| `xiaozhang` | 王校长 | 全校总览、月调度、督办 |
| `zongguan` | 张总管 | 三个演示食堂的运营、周排查与师生反馈受理 |
| `jianguan` | 李监管员 | 检查、问题复核、排程 |
| `caigou` | 周采购员 | 已授权食堂的已发布采购需求、采购到货与验收；独立采购员角色，密码见本机配置 |
| `chushi` | 赵厨师 | 每日食谱制作、实际用料、供餐与食品留样；独立厨师角色 |
| `baojie` | 周保洁员 | 所属食堂环境清洁、餐饮具与设备消毒；独立保洁员角色 |
| `xuesheng` | 陈同学 | 师生反馈 |
| `jiaoshi` | 刘老师 | 师生反馈隔离演示 |

登录后按身份进入不同工作台与导航：管理员处理账号、配置和审计；校长查看全校风险、报表、月度调度并发起督办；总管编排发布食谱和采购需求、维护供应商、审核工作人员健康档案、开展每日管控和周排查，监督执行并受理回复反馈；监管人员负责检查、问题复核和风险排程；采购员只处理已发布采购需求及采购验收；厨师负责已发布食谱的制作、用料、供餐和食品留样；保洁员负责清洁消毒。所有普通工作人员的问题和反馈列表仅包含本人获派任务，健康档案仅显示本人，能够自行建档、修改草稿、提交审核并查看退回原因及历史；全校统计和风险评分不向普通工作人员开放。总管没有采购验收、厨房用料、供餐或现场留样清洁消毒的执行按钮。页面导航和服务端权限同时校验；直接输入无权限页面地址会返回工作台。原账号保留，`chushi`、`baojie` 按独立角色授权；旧 `STAFF` 不再自动拥有这些执行权限。

## 使用方法

1. 按“打包运行”启动服务，打开 `http://127.0.0.1:8080/#/login`。新安装使用首次启动创建的 `admin` 和已设置或日志中生成的密码；当前电脑的演示环境可使用上表账号。
2. 管理员进入“账号与基础配置”创建校长、总管、监管和各岗位工作账号，为总管及普通工作人员选择食堂范围。师生在登录页点击“自行注册”，选择学生或教师身份；注册用户名为 4 至 32 位字母、数字或下划线，密码为 8 至 64 位，两次输入须一致。
3. 总管依次维护供应商、食材和菜品原料，编排发布采购需求及每日食谱。采购员完成到货与验收；厨师处理已发布食谱、实际用料、制作、供餐与留样；保洁员登记清洁消毒。各岗位分别登录自己的账号操作。
4. 监管人员开展检查、创建问题单并复核整改，使用风险、追溯和排程功能；师生提交反馈后，由所选食堂总管受理、组织调查和回复。工作人员健康档案按下节流程提交审核。
5. 校长、监管人员、总管及管理员可按权限查看统计报表；选择起止日期和食堂后查询或导出 CSV。起止日期均包含在统计区间内，一次最多查询 366 天。总管只查看授权食堂，普通工作人员与师生不开放该报表。

完整角色演示顺序见后文“演示顺序”。只有前端页面显示的入口与服务端共同允许的操作才能执行。

## 工作人员健康档案流程

1. 厨师、采购员、保洁员及其他工作人员登录后进入“我的健康档案”，即使没有现有档案也能创建本人草稿，并选择本人已授权的食堂。
2. 填写姓名、联系电话、性别、岗位、健康证明编号、有效期，上传本人健康证明，补充最近培训日期与培训说明。草稿允许暂缺字段；提交时姓名、岗位、健康证明编号、有效期和本人上传的同食堂健康证明附件必填。
3. 提交后显示“待审核”，工作人员不能改写已提交版本；所属食堂总管进入“人员健康与审核”，在审核区域查看并批准，或填写原因后退回。
4. 通过后生成正式档案，校长、监管人员可在“人员健康”查看全校已批准工作人员，总管只看授权食堂；草稿、待审核和被退回的首次申请不进入正式名单。工作人员可在本人页面查看自己的全部提交和审核历史。
5. 被退回或需要更新时创建修订草稿，重新填写、提交、审核，不覆盖旧提交记录。已有批准档案在修订审核期间继续显示旧批准版本；新的修订批准后再更新正式资料。健康证明临期提醒只根据已批准资料计算，不根据未审核草稿提醒。

原有人员档案升级为待工作人员核实的草稿，不直接标记为已审核。账号角色仍由超级管理员授权，填报的岗位名称不会赋予采购、厨房或保洁权限。

## 开发运行

开发时可在 `backend` 运行 `mvn -q spring-boot:run`，另开一个终端在 `frontend` 运行 `npm.cmd run dev`；Vite 将 `/api` 代理到后端 8080 端口。

可分别在两个 PowerShell 窗口执行：

```powershell
# 窗口一：从 backend 目录启动后端
Set-Location .\backend
mvn -q spring-boot:run
```

```powershell
# 窗口二：从 frontend 目录启动开发服务器
Set-Location .\frontend
npm.cmd run dev
```

打开 Vite 终端显示的本地地址并访问 `/#/login`，默认通常为 `http://127.0.0.1:5173/#/login`，端口被占用时以终端输出为准。开发模式直接访问 Vite 地址；打包模式访问后端 8080 地址。

后端可通过 `PORT` 环境变量修改端口；若开发时修改后端端口，需同步调整 `frontend/vite.config.ts` 中 `/api` 的代理目标。附件目录由 `UPLOAD_DIR` 指定，未指定时相对于后端启动目录使用 `./data/uploads`。

## 输入输出示例

### 网页业务示例

以下为人工录入的示例值，相关食堂、菜品与账号应已建立并授权；输出随当前数据和处理进度变化。

| 操作身份与场景 | 输入示例 | 预期输出 |
|---|---|---|
| 总管保存每日食谱 | 第一食堂、`2026-10-18`、午餐、已有菜品“番茄炒蛋”、预计 100 份 | 保存为草稿，总管列表可见；发布后本食堂厨师才能看到 |
| 师生提交反馈 | 第一食堂、实际发生时间、类别“卫生问题”、描述“取餐台有污渍，请核查”；图片可选 | 生成反馈编号，初始状态“待受理”，进入所选食堂总管的待受理列表；提交人可查看进度 |
| 工作人员提交健康档案 | 本人姓名、岗位、健康证明编号、有效期、本人上传的同食堂健康证明附件 | 提交后进入“待审核”；总管批准后才进入正式名单，退回时显示原因 |

### 接口示例一：登录与读取当前用户

后端运行后，另开一个 PowerShell 窗口执行。接口使用会话 Cookie 和 CSRF 校验：先获取令牌，登录时携带同一会话，登录成功后重新获取令牌。登录接口接收表单字段 `username`、`password`；其余接收请求体的业务接口通常使用 JSON。

```powershell
$baseUrl = 'http://127.0.0.1:8080'
$csrf = Invoke-RestMethod -Uri "$baseUrl/api/auth/csrf" -SessionVariable session
$loginCredential = Get-Credential -UserName admin -Message '请输入本机管理员密码'
$login = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post `
    -WebSession $session -Headers @{ 'X-XSRF-TOKEN' = $csrf.token } `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ username = $loginCredential.UserName; password = $loginCredential.GetNetworkCredential().Password }
$login | ConvertTo-Json
$csrf = Invoke-RestMethod -Uri "$baseUrl/api/auth/csrf" -WebSession $session
$profile = Invoke-RestMethod -Uri "$baseUrl/api/auth/me" -WebSession $session
$profile | ConvertTo-Json -Depth 5
```

登录成功返回 HTTP 200，JSON 内容为：

```json
{
  "ok": true,
  "username": "admin"
}
```

当前用户接口返回 HTTP 200，以下以新数据库中编号为 1 的初始管理员为例；实际编号、角色和食堂范围以响应为准，JSON 字段顺序不固定：

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "超级管理员",
  "roles": ["ADMIN"],
  "canteenIds": []
}
```

管理员可查看全校数据，初始 `canteenIds` 为空不影响其管理权限。后续发送 `POST`、`PUT` 等写入请求时，需要继续携带 `$session` 和最新的 `X-XSRF-TOKEN`。

### 接口示例二：查询统计并导出 CSV

沿用上例登录后的会话，输入起止日期和食堂编号。下面假设第一食堂编号为 1；实际编号可通过同一会话请求 `GET /api/canteens` 查询。

```powershell
$query = 'from=2026-10-01&to=2026-10-18&canteenId=1'
$report = Invoke-RestMethod -Uri "$baseUrl/api/reports/metrics?$query" -WebSession $session
$report.canteens | ConvertTo-Json -Depth 5
Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/reports/export?$query" `
    -WebSession $session -OutFile .\food-safety-report-2026-10-01-2026-10-18.csv
```

统计接口返回 HTTP 200，完整响应包含 `from`、`to`、`canteens` 和统计口径说明 `definitions`。新数据库尚无业务记录时，`canteens` 中单个食堂记录示例为：

```json
{
  "canteenId": 1,
  "canteenName": "第一食堂",
  "inspectionCount": 0,
  "issueCount": 0,
  "closedIssueCount": 0,
  "rectificationRate": null,
  "overdueIssueCount": 0,
  "rejectedBatchCount": 0,
  "feedbackCount": 0,
  "handledFeedbackCount": 0,
  "feedbackHandlingRate": null
}
```

没有问题单或反馈时，相应比例为 `null`，页面显示“无问题单”或“无反馈”。统计区间内创建的问题单按当前关闭状态计算整改完成率，区间内创建的反馈按当前已回复或已关闭状态计算处理率；检查数按区间内完成的检查计算。

导出接口返回带 UTF-8 BOM 的 CSV；上述命令将文件保存到当前 PowerShell 工作目录。对应内容示例为：

```csv
食堂,已完成检查,问题数,已关闭问题,整改完成率(%),逾期问题,拒收批次,反馈数,已处理反馈,反馈处理率(%)
"第一食堂","0","0","0",,"0","0","0","0",
```

网页“导出 CSV”按钮下载的文件名为 `食堂食安统计_2026-10-01_2026-10-18.csv`；接口下载响应给出的文件名为 `food-safety-report-2026-10-01-2026-10-18.csv`。

### 接口错误示例

未携带登录会话请求 `GET /api/auth/me`，返回 HTTP 401：

```json
{
  "error": "请先登录"
}
```

统计请求传入反向日期，例如 `from=2026-10-18&to=2026-10-01`，在已登录且有报表权限时返回 HTTP 400：

```json
{
  "error": "请选择不超过366天的有效统计区间"
}
```

## 验证

```powershell
Set-Location .\backend
mvn -q test
cd ..\frontend
npm.cmd test
cd ..
if (Test-Path .\scripts\role_access_smoke.py) { python scripts\role_access_smoke.py }
if (Test-Path .\scripts\daily_menu_readonly_check.py) { python scripts\daily_menu_readonly_check.py }
```

公开源码包含前后端测试；`scripts` 是未纳入公开仓库的本地检查与维护脚本，只有本机已有这些文件时才会运行上述可选接口检查。接口检查需要后端正在运行。`role_access_smoke.py` 只读取数据和验证被拒绝的越权请求；`daily_menu_readonly_check.py` 核对既有食谱的日期格式、总管草稿可见和厨师发布可见，不创建业务记录。后端单元测试覆盖算法、岗位权限、食堂范围和个人任务；前端 Vitest 覆盖角色导航及 UTC 跨日时的食谱列表筛选。

`DutiesWorkflowIntegrationTest` 可通过环境变量 `RUN_DB_WORKFLOW_TESTS=true` 单独启用，需要已完成迁移且存在本机工作账号；它在真实 MySQL 事务内验证采购草稿、发布、到货验收、食谱发布、厨师制作供餐和保洁记录，测试自动回滚，并在事务结束后核对没有留下新增业务记录。普通 `mvn -q test` 跳过这个需要数据库的测试。

需要生成完整模拟数据时，仅在独立可重置数据库中运行 `smoke_test.py`、`p1_smoke.py`、`feedback_routing_smoke.py`，并显式设置 `CAMPUS_SMOKE_DISPOSABLE_DB=1`。这些脚本会新增模拟业务记录；按主流程、补充流程、反馈流程的顺序运行，不应用于当前日常使用的数据库。`p1_smoke.py` 使用厨师本人上传、填报、提交和总管审核流程，覆盖退回理由、再次提交、正式名单可见及修订期间原批准快照保留。`ensure_worker_roles.py` 用于幂等配置本机岗位账号，保留原账号密码与食堂范围，不清理业务数据。

本机演示库曾清空联调生成的采购、食材、菜品、供餐、检查、整改、反馈、审计记录及附件；保留上表的一组账号及其角色和食堂权限、三个初始食堂、检查模板与风险参数。每日食谱功能上线时曾为已有 `chushi` 账号补充“厨师”岗位说明，未填造健康证明；健康审核升级将旧档案转为草稿，须工作人员自行核实并提交审核。`ensure_demo_chef_position.py` 现仅只读核对厨师真实角色和已有健康提交记录，不再补写岗位或健康资料。清理前的数据库和附件备份位于 `backend/data/backups/before_demo_cleanup_20260925_195824`；每日食谱结构升级前的备份为 `backend/data/backups/before_daily_menu_20260925_214908.sql`；岗位与采购发布结构升级前的备份为 `backend/data/backups/before_worker_duties_20260926_111214.sql`；工作人员健康审核升级前已备份数据库至 `backend/data/backups/before_staff_health_review_20260926_174712.sql`。需要演示完整业务时，可按清晰的名称自行录入样例，或在可随时重置的独立测试库运行联调脚本。

本机还用 `scripts/backup_restore_verify.py` 做过一次数据库和附件的备份恢复核对：将同一时间点的备份恢复到独立测试库，比较主要业务记录数量和附件校验值。该脚本要求一个空的恢复测试库，不能直接对已有数据的库重复运行。

## 数据与配置

- 数据表由 `backend/src/main/resources/db/migration` 管理，不要手工修改已执行的迁移文件。
- 附件默认放在 `backend/data/uploads`；可通过 `UPLOAD_DIR` 改变。数据库只保存受控访问路径和元数据。文件上限 8 MB，支持 PNG、JPEG、WebP、PDF；反馈只接收图片。
- 本地数据库配置、管理员与演示账号密码均放在 `backend/config` 的忽略文件中；环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 也可覆盖默认连接参数。
- 学校业务日期与风险定时计算按 `Asia/Shanghai` 处理。数据库 SQL `DATE` 按 `YYYY-MM-DD` 序列化，食谱和采购日期按业务日期比较，避免时区转换造成日期跨日、食谱保存后列表为空。创建、发布、制作等时间记录保留完整时间戳。
- 数据库备份需同时保存附件目录，恢复时应让数据库与附件来自同一个备份时间点。

## 演示顺序

1. 管理员查看三所食堂、账号权限和检查模板；创建或使用演示账号。
2. 食堂总管维护供应商、食材、菜品和原料清单；保存食谱及采购需求草稿，确认总管列表显示而厨师、采购员均看不到未发布内容；总管分别发布采购需求和每日食谱。采购员按需求登记采购到货并完成验收；厨师查看食谱、登记合格批次实际用料、确认制作并供餐。
3. 监管人员查询一个批次关联的两个菜品，查看菜品到供应商的反向路径。
4. 监管人员完成现场检查并下发问题单；工作人员提交整改，监管人员退回后再次复核。
5. 师生自行注册、选择食堂并提交反馈及现场图片；所属食堂总管立即收到待办，分派调查并回复师生；监管人员查看反馈全程，必要时另建整改问题单。
6. 查看风险评分原因和历史，设置监管人员可用时间，生成并确认下一周排程建议。
7. 校长查看监管总览与统计报表，导出 CSV。
8. 厨师、采购员或保洁员在“我的健康档案”自行填报并上传证明，总管进入“人员健康与审核”处理提交；演示一次说明原因退回、修改重提、审核批准，再以校长或监管身份在“人员健康”确认正式名单。厨师录入食品留样，保洁员记录环境清洁和餐饮具消毒；总管查看执行记录并对照采购需求量、到货量与验收合格量。
9. 负责人生成周排查或月调度草稿并人工确认；校长发起督办，查看站内提醒。

## 当前边界

系统只覆盖一所学校的校内食堂和校内监管。外部监管平台、供应商登录、支付、自动传感器采集和真实身份认证不在范围内。留样、消毒、清洁记录使用固定的演示字段；正式使用时需要按学校实际表单调整字段和留存要求。健康证明有效期提醒基于已由食堂总管批准的人工填报资料，不验证证件真伪，也不自动作资格判定。周期报告由系统汇总生成草稿，负责人仍须填写结论并确认。

## 许可证

本项目采用 [MIT License](./LICENSE)，允许在保留版权声明和许可声明的前提下使用、复制、修改和分发。项目使用的第三方依赖适用各自的许可证。
