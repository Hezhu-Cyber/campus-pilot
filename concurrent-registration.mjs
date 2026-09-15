// ============================================================
// 限量报名并发测试脚本（Node 18+，自带 fetch，无需安装依赖）
// ============================================================
// 用法：
//   node concurrent-registration.mjs
//
// 环境变量（可省略，有默认值）：
//   BASE        后端地址；默认 http://127.0.0.1:5173/api （vite 开发代理）
//               如果直接用后端 8081，改成 http://127.0.0.1:8081（不要 /api）
//   PASS_ID     限量报名凭证 ID，默认 1（强烈建议改成你新建的那个）
//   USERS       参与抢名额的账号数，默认 30
//   CONCURRENCY 每一批同时发出的请求数，默认 10（分批发）
//   SAME_USER   true = 用同一个账号并发连点（测"重复报名"），默认 false
//   SEED_PHONE  生成测试手机号的起始值，默认 13900000000
//
// 前置条件：
//   1. MySQL / Redis / RocketMQ / 后端 / 前端 都已启动
//   2. 后端开启了演示验证码（EXPOSE_DEMO_CODE=true）
// ============================================================

const BASE = process.env.BASE || 'http://127.0.0.1:5173/api';
const PASS_ID = Number(process.env.PASS_ID || 1);
const USERS = Number(process.env.USERS || 30);
const CONCURRENCY = Number(process.env.CONCURRENCY || 10);
const SAME_USER = process.env.SAME_USER === 'true';
const SEED_PHONE = Number(process.env.SEED_PHONE || 13900000000);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function json(res) { return res.json(); }

// 1. 发验证码（后端演示模式会直接返回验证码）
async function sendCode(phone) {
  const res = await fetch(`${BASE}/user/code?phone=${phone}`, { method: 'POST' });
  const body = await json(res);
  if (!body.success) throw new Error(`sendCode 失败: ${body.errorMsg}`);
  if (!body.data) throw new Error('验证码为空：请把后端 EXPOSE_DEMO_CODE 设为 true');
  return body.data;
}

// 2. 验证码登录，拿到 token
async function login(phone, code) {
  const res = await fetch(`${BASE}/user/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone, code }),
  });
  const body = await json(res);
  if (!body.success) throw new Error(`login 失败: ${body.errorMsg}`);
  return body.data; // token
}

// 3. 点报名（限量入口）
async function register(token) {
  const res = await fetch(`${BASE}/activity-registration/limited/${PASS_ID}`, {
    method: 'POST',
    headers: { authorization: token },
  });
  return json(res);
}

// 4. 轮询报名最终状态（成功/失败/已补偿）
async function pollStatus(token, registrationId) {
  for (let i = 0; i < 60; i++) {
    const res = await fetch(`${BASE}/activity-registration/${registrationId}/status`, {
      headers: { authorization: token },
    });
    const body = await json(res);
    const s = body.data && body.data.status;
    if (s === 'SUCCESS' || s === 'FAILED' || s === 'COMPENSATED') return body.data;
    await sleep(300);
  }
  return { status: 'TIMEOUT', registrationId };
}

async function main() {
  console.log('参数: PASS_ID=%s USERS=%s CONCURRENCY=%s SAME_USER=%s BASE=%s',
    PASS_ID, USERS, CONCURRENCY, SAME_USER, BASE);

  // 准备账号
  const accounts = [];
  if (SAME_USER) {
    const phone = String(SEED_PHONE);
    const code = await sendCode(phone);
    const token = await login(phone, code);
    for (let i = 0; i < USERS; i++) accounts.push({ phone, token });
    console.log(`同一账号 ${phone} 将并发点 ${USERS} 次`);
  } else {
    for (let i = 0; i < USERS; i++) {
      const phone = String(SEED_PHONE + i);
      const code = await sendCode(phone);
      const token = await login(phone, code);
      accounts.push({ phone, token });
    }
    console.log(`已准备 ${accounts.length} 个不同账号`);
  }

  // 分批并发点报名
  const results = [];
  for (let start = 0; start < accounts.length; start += CONCURRENCY) {
    const batch = accounts.slice(start, start + CONCURRENCY);
    const batchResults = await Promise.all(batch.map(async (acc) => {
      const body = await register(acc.token);
      return { phone: acc.phone, token: acc.token, body };
    }));
    results.push(...batchResults);
    await sleep(200);
  }

  // 统计受理结果
  let accepted = 0, rejected = 0;
  const pending = [];
  for (const r of results) {
    const data = r.body.data || {};
    if (r.body.success && data.registrationId) {
      accepted++;
      pending.push({ phone: r.phone, token: r.token, registrationId: data.registrationId, initial: data.status });
    } else {
      rejected++;
      console.log(`[受理失败] phone=${r.phone} 原因=${r.body.errorMsg || data.failureReason || data.status || '未知'}`);
    }
  }
  console.log(`\n受理结果: 进入异步流程=${accepted}，直接被拒=${rejected}\n`);

  // 轮询最终状态
  const finalStates = { SUCCESS: 0, FAILED: 0, COMPENSATED: 0, TIMEOUT: 0 };
  for (const p of pending) {
    const fin = await pollStatus(p.token, p.registrationId);
    finalStates[fin.status] = (finalStates[fin.status] || 0) + 1;
    if (fin.status === 'FAILED' || fin.status === 'COMPENSATED') {
      console.log(`[最终失败] phone=${p.phone} 流水=${p.registrationId} 状态=${fin.status} 原因=${fin.failureReason || ''}`);
    }
  }
  console.log('\n================ 最终结果统计 ================');
  console.log('成功(SUCCESS):', finalStates.SUCCESS);
  console.log('失败(FAILED):', finalStates.FAILED);
  console.log('已补偿(COMPENSATED):', finalStates.COMPENSATED);
  console.log('超时未决(TIMEOUT):', finalStates.TIMEOUT);
  console.log('==============================================');
  console.log('\n下一步：去 MySQL / Redis 核对是否超卖，见下方清单。');
}

main().catch((e) => { console.error('脚本出错:', e.message); process.exit(1); });
