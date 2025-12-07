# Clear 插件

一个现代化的 Minecraft 插件，用于清理世界中的实体，具有智能实体保留和各种监控功能。

## 功能特性

### 实体清理
- **智能实体保留**：自动保留有价值的实体（已命名、穿戴装备、被驯服、正在繁殖等）
- **基于网格的密度检查**：只清理超过可配置密度限制的实体
- **实体分类**：将实体分为6个类别，实现精细化控制
  - 怪物
  - 动物
  - 物品
  - 载具
  - 投射物
  - 特殊实体
- **自动清理**：根据服务器实体数量和TPS自动执行清理
- **动物回收系统**：将清理的实体按配置概率转换为刷怪蛋

### 监控功能
- **TPS监控**：跟踪服务器TPS并广播状态变化
- **液体限制**：限制液体流动，防止卡顿
- **红石限制**：检测并清理高频红石电路
- **作物限制**：监控作物生长事件，防止卡顿

## 安装方法

1. 从 [GitHub Releases](https://github.com/HotWaterFlask/clear/releases) 下载最新的插件JAR文件
2. 将JAR文件放入服务器的 `plugins` 目录
3. 重启服务器以生成配置文件
4. 在 `plugins/clear/` 目录中编辑配置文件
5. 使用 `/clear reload` 重载插件或重启服务器

## 配置说明

插件使用全面的配置文件 (`config.yml`)，包含以下主要部分：

### 清理设置
```yaml
clear:
  checkInterval: 300          # 自动清理间隔（秒）
  startClearEntitys: 1000     # 开始清理的实体数量阈值
  mustClear:
    amount: 5000              # 强制清理的实体数量阈值
    level: 3                  # 强制清理的清理等级
  tip: true                   # 是否广播清理消息
```

### 实体分类
```yaml
entity-categories:
  monsters:
    enabled: true             # 是否清理怪物
    enableDensityCheck: true  # 是否对怪物使用密度检查
    gridSize: 10              # 密度检查的网格大小
    maxPerGrid: 5             # 每个网格的最大实体数量
    types:                    # 要清理的怪物类型
      - ZOMBIE
      - SKELETON
      # ... 更多怪物类型
```

### 动物回收系统
```yaml
animal-recycling:
  enabled: true              # 是否启用动物回收系统
  clearTypes:
    monsters:
      enabled: true          # 是否回收怪物
      types:                 # 怪物类型及其刷怪蛋概率
        - ZOMBIE 400
        - SKELETON 400
    animals:
      enabled: true          # 是否回收动物
      types:                 # 动物类型及其刷怪蛋概率
        - COW 500
        - PIG 500
```

## 命令列表

| 命令 | 描述 | 用法 | 权限 |
|------|------|------|------|
| `/clear reload` | 重载插件配置 | `/clear reload` | `clear.use` |
| `/clear info` | 显示实体信息 | `/clear info` | `clear.use` |
| `/clear start` | 使用特定等级执行清理（0-3） | `/clear start 2` | `clear.use` |

## 权限说明

| 权限 | 描述 | 默认值 |
|------|------|--------|
| `clear.use` | 允许使用所有插件命令 | OP |

## 实体保留规则

插件自动保留以下实体：
1. 已命名实体（有自定义名称）
2. 穿戴装备的实体（盔甲、武器、工具）
3. 有目标或被吸引的实体
4. 正在繁殖的动物或有宝宝的动物
5. 被驯服的实体（宠物）
6. 已交易的村民（有交易经验）
7. 被乘坐或正在乘坐其他实体的实体
8. Citizens插件的NPC

## 性能考虑

- 插件使用异步处理执行清理操作，避免阻塞主线程
- 密度检查使用基于网格的计数，优化性能
- 实体操作使用分批处理，减少服务器负载
- 红石和液体监控使用冷却机制，防止消息 spam

## 兼容性

- **Minecraft版本**：1.13+（优化支持1.21.10）
- **服务器软件**：Spigot/Paper
- **Java版本**：17+

## 从源码构建

1. 克隆仓库：`git clone https://github.com/HotWaterFlask/clear-plugin.git`
2. 进入项目目录：`cd clear-plugin`
3. 使用Maven构建：`mvn clean package`
4. JAR文件将生成在 `target` 目录中

## 贡献

欢迎贡献！请随时提交Pull Request。

## 许可证

本项目采用MIT许可证 - 详见 [LICENSE](LICENSE) 文件。

## 鸣谢

- **作者**：HotWaterFlask
- **原始插件**：基于fyxridd的"clear"插件概念
- **API**：Spigot/Paper API

## 支持

如果您遇到任何问题或有疑问，请在 [GitHub Issues](https://github.com/HotWaterFlask/clear/issues) 页面提交问题。
