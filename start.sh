#!/bin/bash
# =============================================================================
# 兼容旧入口：保留 start.sh 文件名，实际转发到全新的安装脚本。
#
# 与上游版本的区别（重要）：
#   - 上游会 rm -rf /home/feiyangdigitalbotconf（**会连带删掉 config.json**），
#     再从上游仓库下载 bot.sql / config.json / docker-compose.yml，
#     而本 fork 的 config.json 字段与数据库结构都和上游不同，照做会装成上游版本。
#   - 这里改为调用本仓库的 deploy/install.sh：不删数据、不覆盖已有配置、镜像指向本 fork。
#
# 用法：
#   bash start.sh             # 安装
#   bash start.sh --upgrade   # 升级镜像
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$SCRIPT_DIR/deploy/install.sh" ]; then
    exec bash "$SCRIPT_DIR/deploy/install.sh" "$@"
fi

# 单独下载本文件运行（没有仓库其它文件）时，先把安装脚本拉下来
RAW_BASE="${RAW_BASE:-https://raw.githubusercontent.com/Cnievana/feiyangdigital-bot/main}"
TMP_SH="$(mktemp /tmp/feiyang-install.XXXXXX.sh)"
echo "正在下载安装脚本：$RAW_BASE/deploy/install.sh"
curl -fsSL "$RAW_BASE/deploy/install.sh" -o "$TMP_SH" || {
    echo "下载安装脚本失败，请检查网络，或克隆仓库后执行 bash deploy/install.sh" >&2
    exit 1
}
exec bash "$TMP_SH" "$@"
