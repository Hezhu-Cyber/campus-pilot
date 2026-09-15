# 校园派前端（新版）

基于 **Vue 3 + Vite + Element Plus** 的现代单页应用，保留了原版全部功能。

## 技术栈
- Vue 3（组合式 API）、Vue Router（hash 模式）
- Element Plus 组件库
- Vite 构建
- 无构建的本地托管脚本 `server.mjs`（静态文件 + `/api` 反向代理）

## 快速开始

```bash
# 1. 安装依赖（首次）
npm install

# 2. 开发模式（热更新，端口 5173，自动代理 /api 到后端 8081）
npm run dev

# 3. 生产构建 + 本地托管（端口 8080）
npm run build
npm run serve
```

访问 `http://localhost:8080`（`npm run serve`）或 `http://localhost:5173`（`npm run dev`）。

## 目录说明
- `src/views/`：全部页面视图
- `src/api.js`：Axios 封装（统一处理 token、Result 解包、`imgUrl` 图片路径规范化）
- `src/router.js`：路由与登录/角色守卫
- `public/imgs/`：图片资源目录（后端上传目录指向这里）
- `server.mjs`：生产托管脚本，`/api/*` 代理到后端，`/imgs/*` 直接读取 `public/imgs`
- `frontend-legacy/`：旧版前端备份

## 常用账号
| 角色 | 手机号 | 说明 |
|---|---|---|
| 学生 | 13800000003 | 验证码登录（验证码在 Redis；默认不直接返回，演示可用 EXPOSE_DEMO_CODE=true 开启） |
| 组织者 | 13800000002 | 进入组织者工作台 |
| 管理员 | 13800000001 | 进入管理后台 |
