// ============================================================
// 生成 JMeter / 压测脚本用的测试账号 CSV：phone,code
// 验证码获取方式（自动）：
//   1) 后端开启 EXPOSE_DEMO_CODE=true 时，接口直接返回验证码；
//   2) 否则自动从 Redis 读取（键 login:code:{phone}），无需重启后端。
// 用法：
//   node gen-users-csv.mjs
// 环境变量：
//   BASE  默认 http://127.0.0.1:8081（直连后端，无需启动前端）
//   N     生成几个账号，默认 30
//   SEED  手机号起始值，默认 13900010000（换一批就 +10000）
//   OUT   输出文件名，默认 users.csv
// ============================================================
import fs from 'node:fs';
import net from 'node:net';

const BASE = process.env.BASE || 'http://127.0.0.1:8081';
const N = Number(process.env.N || 30);
const SEED = Number(process.env.SEED || 13900010000);
const OUT = process.env.OUT || 'users.csv';

// ---- 极简 Redis 客户端（只支持 AUTH / GET，无第三方依赖）----
function redisCommand(args) {
  const host = process.env.REDIS_HOST || '127.0.0.1';
  const port = Number(process.env.REDIS_PORT || 6379);
  return new Promise((resolve) => {
    let buf = '';
    let done = false;
    const sock = net.createConnection({ host, port });
    sock.setTimeout(4000);
    sock.on('connect', () => {
      let cmd = `*${args.length}\r\n`;
      for (const a of args) cmd += `$${Buffer.byteLength(a)}\r\n${a}\r\n`;
      sock.write(cmd);
    });
    sock.on('data', (d) => {
      if (done) return;
      buf += d.toString();
      const i = buf.indexOf('\r\n');
      if (i < 0) return;
      const head = buf.slice(0, i);
      const c = head[0];
      if (c === '$') {
        const len = Number(head.slice(1));
        if (len === -1) { done = true; sock.end(); return resolve(null); }
        if (buf.length < i + 2 + len + 2) return; // 数据还没收完，继续等
        done = true;
        sock.end();
        return resolve(buf.slice(i + 2, i + 2 + len));
      }
      done = true;
      sock.end();
      resolve(c === '-' ? null : head.slice(1));
    });
    sock.on('error', () => { if (!done) { done = true; resolve(null); } });
    sock.on('timeout', () => { if (!done) { done = true; sock.destroy(); resolve(null); } });
  });
}

async function redisGet(key) {
  if (process.env.REDIS_PASSWORD) {
    const auth = await redisCommand(['AUTH', process.env.REDIS_PASSWORD]);
    if (auth === null) return null;
  }
  return redisCommand(['GET', key]);
}
// ------------------------------------------------------------

// 发验证码；接口不返回时就到 Redis 里读
async function getCode(phone) {
  const res = await fetch(`${BASE}/user/code?phone=${phone}`, { method: 'POST', signal: AbortSignal.timeout(10000) });
  const body = await res.json();
  if (body.success && body.data) return body.data;
  const fromRedis = await redisGet(`login:code:${phone}`);
  if (fromRedis) return fromRedis;
  throw new Error(`phone=${phone} 验证码获取失败：${body.errorMsg || '接口未返回且 Redis 也读不到（请确认 Redis 在 6379 运行）'}`);
}

let out = 'phone,code\n';
for (let i = 0; i < N; i++) {
  const p = String(SEED + i);
  const code = await getCode(p);
  out += `${p},${code}\n`;
  if ((i + 1) % 200 === 0) console.log(`已生成 ${i + 1}/${N}...`);
}
fs.writeFileSync(OUT, out, 'utf8');
console.log(`已生成 ${OUT}，共 ${N} 个测试账号`);
