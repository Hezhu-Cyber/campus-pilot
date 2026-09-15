import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const dist = path.join(__dirname, 'dist')
const BACKEND = process.env.BACKEND_URL || 'http://127.0.0.1:8081'
const PORT = Number(process.env.PORT || 8080)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json'
}

function proxy(req, res) {
  const target = new URL(BACKEND)
  const options = {
    hostname: target.hostname,
    port: target.port,
    method: req.method,
    path: req.url.replace(/^\/api/, ''),
    headers: { ...req.headers, host: target.host }
  }
  const preq = http.request(options, (pres) => {
    res.writeHead(pres.statusCode, pres.headers)
    pres.pipe(res)
  })
  preq.on('error', () => { res.writeHead(502, { 'Content-Type': 'text/plain' }); res.end('Bad Gateway: backend unreachable') })
  req.pipe(preq)
}

const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api')) return proxy(req, res)
  let urlPath
  try { urlPath = decodeURIComponent(new URL(req.url, 'http://localhost').pathname) } catch { urlPath = '/' }
  if (urlPath === '/') urlPath = '/index.html'
  // 上传图片直接取自 public/imgs，避免构建后新增图片不在 dist 中
  const staticRoot = urlPath.startsWith('/imgs/') ? path.join(__dirname, 'public') : dist
  const filePath = path.join(staticRoot, path.normalize(urlPath))
  if (!filePath.startsWith(staticRoot)) { res.writeHead(403); return res.end('forbidden') }
  fs.stat(filePath, (err, st) => {
    if (err || !st.isFile()) {
      res.writeHead(200, { 'Content-Type': MIME['.html'] })
      return fs.createReadStream(path.join(dist, 'index.html')).pipe(res)
    }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream' })
    fs.createReadStream(filePath).pipe(res)
  })
})

server.listen(PORT, () => console.log(`CampusPilot web running at http://localhost:${PORT}  (API -> ${BACKEND})`))

