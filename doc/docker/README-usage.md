# Skill Docker Sandbox 使用指南

## 📦 快速开始

### 1. 构建镜像

```powershell
cd E:\worksapce\sts5.1\legend-smartmind\legend-smartmind\docker

# 构建通用沙箱镜像（约 1.8GB）
docker-compose build skill-sandbox

# 构建浏览器沙箱镜像（约 2.3GB，可选）
docker-compose build skill-browser-sandbox
```

### 2. 启动沙箱容器

```powershell
# 启动通用沙箱（文件操作、Python 脚本等）
docker-compose up -d skill-sandbox

# 启动浏览器沙箱（需要浏览器自动化时）
docker-compose up -d skill-browser-sandbox
```

### 3. 查看状态

```powershell
# 查看所有容器状态
docker-compose ps

# 查看运行日志
docker-compose logs -f skill-sandbox

# 进入容器
docker exec -it skill-sandbox bash
```

### 4. 停止沙箱

```powershell
# 停止所有服务
docker-compose down

# 停止特定服务
docker-compose stop skill-browser-sandbox
```

---

## 🌐 浏览器沙箱

### 适用场景
- 网页内容抓取
- 自动化表单填写
- 网页截图/PDF 生成
- 前端 UI 测试

### 构建和启动

```powershell
# 1. 先构建基础沙箱镜像
docker-compose build skill-sandbox

# 2. 构建浏览器沙箱镜像（基于基础镜像，约 2.3GB）
docker-compose build skill-browser-sandbox

# 3. 启动浏览器沙箱
docker-compose up -d skill-browser-sandbox
```

### 浏览器沙箱包含的工具

| 工具 | 用途 |
|------|------|
| Playwright | 浏览器自动化框架 |
| Chromium | 无头浏览器 |
| @playwright/mcp | MCP 协议服务器 |
| 中文字体 | 网页中文渲染支持 |

### 使用示例

```powershell
# 进入浏览器沙箱
docker exec -it skill-browser-sandbox bash

# 验证 Playwright 安装
npx playwright --version

# 验证 Chromium 安装
npx playwright install --dry-run chromium
```

### 资源限制
- **内存**: 1GB（最大）/ 768MB（保留）
- **CPU**: 1.0 核
- **临时存储**: /tmp 128MB（内存）

---

## 🛠️ 管理脚本命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `build` | 构建 Docker 镜像 | `.\manage.ps1 build` |
| `start` | 启动沙箱容器 | `.\manage.ps1 start` |
| `stop` | 停止并删除容器 | `.\manage.ps1 stop` |
| `restart` | 重启容器 | `.\manage.ps1 restart` |
| `logs` | 查看容器日志 | `.\manage.ps1 logs` |
| `exec` | 进入容器 shell | `.\manage.ps1 exec` |
| `status` | 查看状态 | `.\manage.ps1 status` |
| `clean` | 清理资源 | `.\manage.ps1 clean` |

---

## 📖 docker-compose 命令

### 构建
```powershell
# 构建镜像
docker-compose build skill-runtime

# 构建并启动
docker-compose up -d
```

### 启动/停止
```powershell
# 启动所有服务
docker-compose up -d

# 启动特定服务
docker-compose up -d skill-sandbox

# 停止所有服务
docker-compose down

# 停止并删除卷
docker-compose down -v
```

### 查看状态
```powershell
# 查看运行状态
docker-compose ps

# 查看日志
docker-compose logs -f skill-sandbox

# 查看特定行数
docker-compose logs --tail=100 skill-sandbox
```

### 进入容器
```powershell
# 进入容器 shell
docker exec -it skill-test bash

# 在容器中执行命令
docker exec skill-test python3 --version
```

---

## 🔄 动态管理流程

### 场景 1：开发调试
```powershell
# 1. 启动容器
.\manage.ps1 start

# 2. 进入容器调试
.\manage.ps1 exec

# 3. 测试完成后停止
.\manage.ps1 stop
```

### 场景 2：查看日志
```powershell
# 1. 启动容器
.\manage.ps1 start

# 2. 实时查看日志
.\manage.ps1 logs

# 3. 按 Ctrl+C 退出日志查看
```

### 场景 3：清理资源
```powershell
# 完全清理
.\manage.ps1 clean
```

---

## 📊 容器配置

### 资源限制
- **内存**: 600MB（最大）/ 512MB（保留）
- **CPU**: 0.5 核
- **临时存储**: /tmp 64MB（内存）

### 挂载目录
- `./workspaces:/work` - 工作目录（持久化）
- `/tmp` - 临时文件（tmpfs，容器销毁后消失）

### 安全配置
- ✅ 非 root 用户（UID 1000）
- ✅ 只读根文件系统
- ✅ 丢弃所有 Capabilities
- ✅ no-new-privileges

---

## 🐛 常见问题

### Q1: 容器启动失败
```powershell
# 检查 Docker 是否运行
docker info

# 检查镜像是否存在
docker images skill-runtime:latest

# 重新构建
.\manage.ps1 build
```

### Q2: 无法进入容器
```powershell
# 检查容器是否运行
docker ps | grep skill-test

# 如果容器已停止，先启动
.\manage.ps1 start

# 重新进入
.\manage.ps1 exec
```

### Q3: 工作目录为空
```powershell
# 检查工作目录
ls .\workspaces\

# 创建工作目录
mkdir .\workspaces\test
```

### Q4: 内存不足
```powershell
# 查看容器资源使用
docker stats skill-test

# 修改 docker-compose.yml 中的内存限制
# 然后重启
.\manage.ps1 restart
```

---

## 📝 目录结构

```
docker/
├── dockerfile           # Docker 镜像构建文件
├── docker-compose.yml   # Docker Compose 配置
├── manage.ps1          # PowerShell 管理脚本
├── README.md           # 本文档
└── workspaces/         # 工作目录（自动创建）
```

---

## 🔗 相关文档

- [Docker 官方文档](https://docs.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [Skill Runtime 架构设计](../../codexx/claude-code-runtime-contract-architecture.md)
