# 推送代码到 GitHub

## 步骤 1：在 GitHub 创建仓库

1. 打开 https://github.com/new
2. 仓库名填写：`douyin-timer-control`
3. 选择 **Public**（公开）
4. 勾选 **Add a README file**
5. 点击 **Create repository**
6. 复制仓库地址，格式如：`https://github.com/你的用户名/douyin-timer-control.git`

## 步骤 2：推送代码

打开 PowerShell 或命令提示符，执行以下命令：

```bash
cd "C:\Users\jams\WorkBuddy\2026-08-29-14-18-26\douyin-timer-app"

# 添加远程仓库（替换为你的仓库 URL）
git remote add origin https://github.com/你的用户名/douyin-timer-control.git

# 重命名主分支为 main
git branch -M main

# 推送代码
git push -u origin main
```

如果遇到认证问题，需要配置 GitHub Token：
```bash
git remote set-url origin https://你的用户名:你的Token@github.com/你的用户名/douyin-timer-control.git
```

## 步骤 3：触发 GitHub Actions 构建

推送完成后：
1. 打开仓库页面
2. 点击 **Actions** 标签
3. 等待工作流自动运行（约 3-5 分钟）
4. 点击运行记录 → **Artifacts** 下载 APK
