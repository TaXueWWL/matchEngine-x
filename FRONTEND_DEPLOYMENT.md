# Trading Engine X - 前端部署指南

## 项目概述

本指南将指导您完成Trading Engine X前端应用的部署，包括开发环境和生产环境的配置。

## 前端技术栈

- **框架**: Vue 3 + Composition API
- **语言**: TypeScript
- **构建工具**: Vite
- **UI库**: Element Plus
- **状态管理**: Pinia
- **图表库**: ECharts
- **WebSocket**: STOMP.js + SockJS
- **HTTP客户端**: Axios

## 环境要求

- Node.js 16+ 或 18+
- npm 8+ 或 yarn 1.22+
- 现代浏览器（Chrome 88+, Firefox 85+, Safari 14+）

## 开发环境部署

### 1. 安装依赖

```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 或使用yarn
yarn install
```

### 2. 环境变量配置

创建 `.env.development` 文件：

```bash
# 开发环境配置
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=http://localhost:8080/ws
VITE_APP_TITLE=Trading Engine X
```

### 3. 启动开发服务器

```bash
# 启动开发服务器
npm run dev

# 或使用yarn
yarn dev
```

开发服务器将在 http://localhost:3000 启动

### 4. 开发环境特性

- 🔥 热重载 - 代码修改后自动刷新
- 🛠️ TypeScript 类型检查
- 📦 自动导入 - Element Plus 组件和 Vue API
- 🔍 开发工具 - Vue DevTools 支持
- 🔄 API代理 - 自动代理后端API请求

## 生产环境部署

### 方式一：静态文件部署

#### 1. 构建生产版本

```bash
# 构建生产版本
npm run build

# 或使用yarn
yarn build
```

#### 2. 生产环境配置

创建 `.env.production` 文件：

```bash
# 生产环境配置
VITE_API_BASE_URL=https://your-api-domain.com
VITE_WS_URL=https://your-api-domain.com/ws
VITE_APP_TITLE=Trading Engine X
```

#### 3. 部署到静态服务器

构建完成后，`dist` 目录包含所有静态文件，可以部署到：

**Nginx部署示例：**

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /path/to/your/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API代理
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket代理
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**Apache部署示例：**

```apache
<VirtualHost *:80>
    ServerName your-domain.com
    DocumentRoot /path/to/your/dist

    # 单页应用支持
    <Directory /path/to/your/dist>
        AllowOverride All
        Require all granted

        # URL重写支持
        RewriteEngine On
        RewriteBase /
        RewriteRule ^index\.html$ - [L]
        RewriteCond %{REQUEST_FILENAME} !-f
        RewriteCond %{REQUEST_FILENAME} !-d
        RewriteRule . /index.html [L]
    </Directory>

    # API代理
    ProxyPass /api/ http://localhost:8080/api/
    ProxyPassReverse /api/ http://localhost:8080/api/

    # WebSocket代理
    ProxyPass /ws/ ws://localhost:8080/ws/
    ProxyPassReverse /ws/ ws://localhost:8080/ws/
</VirtualHost>
```

### 方式二：Docker部署

#### 1. 创建Dockerfile

```dockerfile
# 多阶段构建
FROM node:18-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制nginx配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

#### 2. 创建nginx.conf

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 启用gzip压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

#### 3. 构建和运行Docker镜像

```bash
# 构建镜像
docker build -t trading-frontend .

# 运行容器
docker run -d -p 3000:80 --name trading-frontend trading-frontend
```

### 方式三：Docker Compose部署

创建 `docker-compose.yml` 文件：

```yaml
version: '3.8'

services:
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    environment:
      - VITE_API_BASE_URL=http://backend:8080
      - VITE_WS_URL=http://backend:8080/ws
    depends_on:
      - backend
    networks:
      - trading-network

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    networks:
      - trading-network

networks:
  trading-network:
    driver: bridge
```

运行：
```bash
docker-compose up -d
```

## 部署验证

### 1. 功能测试清单

- [ ] 页面正常加载
- [ ] 用户ID设置功能
- [ ] 账户余额查询
- [ ] 资金划转功能
- [ ] 交易下单功能
- [ ] 订单撤销和修改
- [ ] 订单簿实时更新
- [ ] K线图正常显示
- [ ] WebSocket连接正常

### 2. 性能检查

```bash
# 使用lighthouse检查性能
npx lighthouse http://your-domain.com --output html --output-path ./lighthouse-report.html

# 检查构建产物大小
npm run build
du -sh dist/
```

### 3. 浏览器兼容性测试

在以下浏览器中测试：
- Chrome 88+
- Firefox 85+
- Safari 14+
- Edge 88+

## 常见问题

### Q1: 前端无法连接后端API

**解决方案：**
1. 检查API地址配置是否正确
2. 确认后端服务正在运行
3. 检查跨域配置（CORS）
4. 验证防火墙和网络配置

### Q2: WebSocket连接失败

**解决方案：**
1. 检查WebSocket URL配置
2. 确认后端WebSocket服务正常
3. 检查代理服务器的WebSocket支持
4. 验证SSL证书（HTTPS环境）

### Q3: 静态资源404错误

**解决方案：**
1. 确认构建输出正确
2. 检查服务器根目录配置
3. 验证URL重写规则
4. 检查文件权限

### Q4: 页面刷新后404错误

**解决方案：**
这是单页应用（SPA）的常见问题，需要配置服务器：

**Nginx：**
```nginx
try_files $uri $uri/ /index.html;
```

**Apache：**
```apache
RewriteEngine On
RewriteCond %{REQUEST_FILENAME} !-f
RewriteCond %{REQUEST_FILENAME} !-d
RewriteRule . /index.html [L]
```

## 性能优化建议

### 1. 构建优化

```javascript
// vite.config.ts
export default defineConfig({
  build: {
    // 代码分割
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['vue', 'vue-router', 'pinia'],
          ui: ['element-plus'],
          charts: ['echarts']
        }
      }
    },
    // 压缩配置
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  }
})
```

### 2. CDN配置

```html
<!-- 在index.html中使用CDN -->
<script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
<script src="https://unpkg.com/element-plus/dist/index.full.js"></script>
```

### 3. 缓存策略

```nginx
# 设置缓存头
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}

location /index.html {
    add_header Cache-Control "no-cache, no-store, must-revalidate";
}
```

## 监控和维护

### 1. 错误监控

推荐集成错误监控服务：
- Sentry
- Rollbar
- Bugsnag

### 2. 性能监控

```javascript
// 添加性能监控
import { createApp } from 'vue'

const app = createApp(App)

// 监控首屏加载时间
window.addEventListener('load', () => {
  const loadTime = performance.now()
  console.log('Page load time:', loadTime)
})
```

### 3. 健康检查

创建健康检查端点：
```javascript
// 在nginx中添加
location /health {
    return 200 'OK';
    add_header Content-Type text/plain;
}
```

---

🎉 **部署完成！** 您的Trading Engine X前端应用现已成功部署。

如需技术支持，请查看日志文件或联系开发团队。