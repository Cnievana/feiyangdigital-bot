
🤖 Feiyang Digital Telegram 群管机器人
===========================================

基于 **SpringBoot** 和 **Telegrambot-Api** 打造的多功能 Telegram 群管理机器人，有效查杀18+违规视频、贴图、图片，AI识别各种博彩，违规图片、文字，可设置正则自定义关键字回复、违规词汇删除等功能，支持每日词云统计，进群欢迎等多种实用群管功能，**Powered By DeepSeek And Google Cloud Vision**。

1️⃣ 开始之前
----------------
# 强烈推荐观看保姆级部署视频教程：[点击观看](https://www.youtube.com/watch?v=QgrPRgR5tek)
# 使用文档：[使用文档.md](使用文档.md)（本 fork 版本，含 AI 客服等新增功能）
- **创建你的 Telegram 机器人:**
  1. 前往 [@BotFather](https://t.me/botfather) 以创建机器人。
  2. 记录下机器人的 `token` 和用户名。
  3. 不熟悉的话，可以[查阅此具体步骤](https://blog.csdn.net/whatday/article/details/113747294)。

- **准备环境:**
  - [安装 Docker 和 Docker-Compose](https://www.wxy97.com/archives/77)。

2️⃣ 一键安装（推荐）
--------------

> 本 fork 新增了数据库字段，**不能**用上游镜像 `youshandefeiyang/feiyangdigital-bot:latest`。
> 本 fork 已提供现成镜像（amd64 / arm64），**不需要自己构建**。

```bash
curl -fsSL https://raw.githubusercontent.com/Cnievana/feiyangdigital-bot/main/start.sh -o start.sh
less start.sh          # 建议先看一眼再执行
bash start.sh
```

脚本会自动：检查 Docker → 建目录 `/home/feiyangdigitalbotconf`（不删已有数据）→ 交互式询问 username / token / DeepSeek Key 并生成 `config.json` → 拉镜像 → 启动。

非交互方式：

```bash
BOT_NAME=你的机器人username BOT_TOKEN=123456789:AA... AI_KEY=sk-xxx bash start.sh
```

也可以 `git clone` 后执行 `bash deploy/install.sh`，或手动按
[使用文档 第三章](使用文档.md#三部署-feiyangdigital-bot) 的方式二自己写 compose。

3️⃣ 配置机器人
----------------

一键脚本已经问过并写好了。手动安装时才需要编辑 `/home/feiyangdigitalbotconf/config.json`：

1. 机器人 **username**（`@` 后面那串，不带 `@`）填入 `botConfig.name`；
2. 机器人 **token** 填入 `botConfig.token`；
3. 你的 **DeepSeek API Key** 填入 `openAIApiKey` 数组。

> `name` 必须填 Username，不能填显示名。详见 [使用文档 3.6](使用文档.md#36-配置机器人)。

▶️ 运行机器人
----------------

- 确保你的网络可以连接到 Telegram 服务器。如果使用软路由，请使用增强代理。
- 在 `/home/feiyangdigitalbotconf/` 目录下执行：

```bash
docker compose up -d
```

- 查看日志（看到 `longPolling模式已启动` 即成功）：

```bash
docker compose logs -f
```

⏸️ 暂停容器
------------

```bash
cd /home/feiyangdigitalbotconf && docker compose stop
```

🔥 重启容器
------------

```bash
cd /home/feiyangdigitalbotconf && docker compose restart
```

> 改过 `config.json` 不要用 `restart`，要用 `docker compose up -d --force-recreate`（原因见使用文档 8.9）。

🔄 更新
--------

```bash
cd /home/feiyangdigitalbotconf
bash install.sh --upgrade
```

等价于：

```bash
docker compose pull
docker compose up -d --force-recreate
```

> ⚠️ **不要**再执行 `docker compose down` + 删除 `mysql-data` 卷。
> 新版启动时会自动补齐数据库缺失字段（见日志「自动迁移」），
> 删卷会丢失全部群配置、关键词与违规记录。
>
> 需要固定版本时，把 `.env` 里的 `BOT_IMAGE` 写成 `nievana/feiyangdigital-bot:v1.9.9` 这样带版本号的形式。

