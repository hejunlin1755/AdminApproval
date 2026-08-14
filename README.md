# AdminApproval — 危险命令审批插件

Minecraft Java Edition 26.1.2 / Paper 26.1.2 服务器插件。
管理员可以正常使用 OP 建筑权限，但执行危险命令前必须提交审批；免审批命令可由服主通过白名单动态配置。

## 功能特性

- **服主身份系统**：`config.yml` 中配置 `owner-uuid` 列表，只有列表中的 UUID 才是腐竹（不按 OP 等级判断）。腐竹拥有所有命令直接执行、管理审批请求、管理白名单、绕过所有限制的权限。
- **管理员逻辑**：其他 OP 玩家视为管理员，可正常使用 `/fill` `/clone` `/setblock` 等建筑命令；危险命令需要提交审批。
- **危险命令审批**：默认需审批命令：`op deop stop restart reload ban pardon whitelist give item execute`。管理员执行如 `/give Steve diamond 64` 会被拦截并生成审批请求，腐竹收到「编号:#1001 申请人:Steve 命令:give Steve diamond 64」，批准后由控制台执行并记录审批历史。
- **命令白名单系统**：服主可通过 `/adminapproval whitelist add|remove|list` 动态管理免审批命令，数据保存在 `plugins/AdminApproval/data.yml`，重启后保留；支持 `/fill` 与 `minecraft:fill` 两种写法。
- **防绕过**：管理员不能批准自己的请求；`/minecraft:give` 前缀无法绕过拦截；只有腐竹 UUID 能执行 `/adminapproval whitelist`。
- **配置免改代码**：`command-settings.yml` 可调整 `dangerous`（危险命令）与 `whitelist`（初始免审批命令）列表。

## 构建

```bash
mvn package
```

构建产物：`target/AdminApproval-1.0.0.jar`，放入服务器 `plugins/` 目录后重启即可。

## 配置文件

### plugins/AdminApproval/config.yml

```yaml
owner-uuid:
  - "填写服主UUID"   # 只有这里的 UUID 才是腐竹
```

### plugins/AdminApproval/command-settings.yml

```yaml
dangerous:
  - op
  - deop
  - stop
  - give
whitelist:
  - fill
  - clone
  - setblock
```

### plugins/AdminApproval/data.yml

运行时数据：待审批请求、审批历史、免审批白名单（自动维护，重启保留）。

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/adminrequest <命令...>` | 提交危险命令审批请求 | adminapproval.request |
| `/adminapprove <编号>` | 批准审批请求 | adminapproval.approve |
| `/adminreject <编号>` | 拒绝审批请求 | adminapproval.approve |
| `/adminrequests` | 查看待审批请求 | adminapproval.approve |
| `/adminhistory [数量]` | 查看审批历史 | adminapproval.approve |
| `/adminapproval whitelist add <命令>` | 添加免审批命令（仅腐竹） | adminapproval.manage |
| `/adminapproval whitelist remove <命令>` | 移除免审批命令（仅腐竹） | adminapproval.manage |
| `/adminapproval whitelist list` | 查看免审批命令（仅腐竹） | adminapproval.manage |

## 测试

构建后运行场景自检（不依赖真实服务器）：

```bash
mvn package
java -cp "target/classes;target/test-classes;<snakeyaml.jar>" cn.fctweb.adminapproval.ScenarioCheck
```

覆盖场景：`/fill` 直通、`/give` 生成审批、腐竹批准由控制台执行、普通管理员管理白名单被拒绝，以及 minecraft: 前缀防绕过、data.yml 白名单持久化等。