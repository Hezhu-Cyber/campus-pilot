// ============================================================
// 创建限量报名（压测用）脚本
// 用法：
//   node create-limited-pass.mjs
// 环境变量（可省略）：
//   BASE        默认 http://127.0.0.1:8081
//   ORGANIZER   组织者手机号，默认 13800000002
//   ACTIVITY_ID 关联活动 ID，默认 1
//   STOCK       名额，默认 100
//   TITLE       标题，默认 "并发压测-100名额"
//   DAYS        报名持续天数，默认 3
// ============================================================
import net from 'node:net';

const BASE = process.env.BASE || 'http://127.0.0.1:8081';
const ORGANIZER = process.env.ORGANIZER || '13800000002';
const ACTIVITY_ID = Number(process.env.ACTIVITY_ID || 1);
const STOCK = Number(process.env.STOCK || 100);
const TITLE = process.env.TITLE || '并发压测-100名额';
const DAYS = Number(process.env.DAYS || 3);

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
        if (buf.length < i + 2 + len + 2) return;
        done = true; sock.end();
        return resolve(buf.slice(i + 2, i + 2 + len));
      }
      done = true; sock.end();
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

const iso = (d) => {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};

// 1. 发验证码
const codeRes = await fetch(`${BASE}/user/code?phone=${ORGANIZER}`, { method: 'POST', signal: AbortSignal.timeout(10000) });
const codeBody = await codeRes.json();
let code = codeBody.data || await redisGet(`login:code:${ORGANIZER}`);
if (!code) throw new Error(`组织者验证码获取失败：${codeBody.errorMsg || '接口未返回且 Redis 读不到'}`);

// 2. 登录
const loginRes = await fetch(`${BASE}/user/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ phone: ORGANIZER, code }),
  signal: AbortSignal.timeout(10000),
});
const loginBody = await loginRes.json();
if (!loginBody.success) throw new Error(`登录失败：${loginBody.errorMsg}`);
const token = loginBody.data;

// 3. 创建限量报名
const now = new Date();
const begin = iso(new Date(now.getTime() - 60 * 1000)); // 1 分钟前开始
const end = iso(new Date(now.getTime() + DAYS * 24 * 60 * 60 * 1000));
const body = {
  activityId: ACTIVITY_ID,
  title: TITLE,
  subTitle: '压测专用',
  rules: '测试数据，可随意报名',
  payValue: 0,
  actualValue: 0,
  stock: STOCK,
  beginTime: begin,
  endTime: end,
};
const createRes = await fetch(`${BASE}/registration-pass/limited`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', authorization: token },
  body: JSON.stringify(body),
  signal: AbortSignal.timeout(10000),
});
const createBody = await createRes.json();
if (!createBody.success) throw new Error(`创建失败：${createBody.errorMsg}`);
const passId = createBody.data;

// 4. 验证 Redis 库存是否已初始化
const redisStock = await redisGet(`registration:{${passId}}:stock`);

console.log('==================================================');
console.log(`新限量报名已创建：ID = ${passId}`);
console.log(`名额 = ${STOCK}，开始 = ${begin}，结束 = ${end}`);
console.log(`Redis 库存键 registration:{${passId}}:stock = ${redisStock}`);
console.log('==================================================');
console.log(`下一步压测命令：`);
console.log(`  $env:PASS_ID='${passId}'`);
console.log(`  node burst-registration.mjs`);
