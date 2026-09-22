// ============================================================
// 1000 并发报名压测脚本
// 用法（分两步）：
//   第一步（只需一次）：生成 1000 个账号
//     $env:N='1000'; node gen-users-csv.mjs
//   第二步：同时打 1000 个报名请求
//     $env:PASS_ID='你的限量报名ID'; node burst-registration.mjs
//
// 环境变量：
//   BASE              后端地址，默认 http://127.0.0.1:8081
//   PASS_ID           限量报名凭证 ID（必改）
//   CSV               账号文件，默认 users.csv
//   LOGIN_CONCURRENCY 登录时并发数，默认 50
//   POLL_CONCURRENCY  查结果时并发数，默认 50
//
// 说明：报名流水 ID 是 64 位雪花 ID，可能超过 JS 安全整数（2^53），
// 这里用"大整数转字符串"的方式解析，避免四舍五入导致查不到状态。
// ============================================================
import fs from 'node:fs';

const BASE = process.env.BASE || 'http://127.0.0.1:8081';
const PASS_ID = Number(process.env.PASS_ID || 0);
const CSV = process.env.CSV || 'users.csv';
const LOGIN_CONCURRENCY = Number(process.env.LOGIN_CONCURRENCY || 50);
const POLL_CONCURRENCY = Number(process.env.POLL_CONCURRENCY || 50);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 解析 JSON：超过安全整数范围的数字转成字符串，避免精度丢失
function parseJson(text) {
  return JSON.parse(text, (k, v) => {
    if (typeof v === 'number' && !Number.isSafeInteger(v)) return String(v);
    return v;
  });
}

// 带超时的请求
async function req(path, { method = 'GET', token, body } = {}) {
  const headers = {};
  if (token) headers.authorization = token;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(10000),
  });
  return parseJson(await res.text());
}

// 通用并发执行器
async function runPool(tasks, concurrency, worker) {
  const results = new Array(tasks.length);
  let next = 0;
  async function loop() {
    while (next < tasks.length) {
      const i = next++;
      results[i] = await worker(tasks[i], i);
    }
  }
  const workers = [];
  for (let i = 0; i < Math.min(concurrency, tasks.length); i++) workers.push(loop());
  await Promise.all(workers);
  return results;
}

async function main() {
  if (!PASS_ID) throw new Error('请先设置 PASS_ID（$env:PASS_ID=\'你的限量报名ID\'）');

  // 1. 读账号文件
  if (!fs.existsSync(CSV)) throw new Error(`找不到 ${CSV}，请先运行 node gen-users-csv.mjs`);
  const lines = fs.readFileSync(CSV, 'utf8').trim().split('\n').filter((l) => l.trim());
  const header = lines[0].toLowerCase();
  const accounts = lines.slice(header.startsWith('phone') ? 1 : 0)
    .map((l) => l.split(','))
    .filter((p) => p.length >= 2 && p[0] && p[1])
    .map((p) => ({ phone: p[0].trim(), code: p[1].trim() }));
  console.log(`账号总数：${accounts.length}`);

  // 2. 登录
  const tokens = await runPool(accounts, LOGIN_CONCURRENCY, async (acc) => {
    try {
      const body = await req('/user/login', {
        method: 'POST',
        body: { phone: acc.phone, code: acc.code },
      });
      if (body.success && body.data) return { phone: acc.phone, token: body.data };
      return { phone: acc.phone, error: body.errorMsg || '登录失败' };
    } catch (e) {
      return { phone: acc.phone, error: e.message };
    }
  });
  const okAccounts = tokens.filter((t) => t.token);
  console.log(`登录成功：${okAccounts.length}，登录失败：${tokens.length - okAccounts.length}`);
  if (!okAccounts.length) throw new Error('没有可用账号，请检查服务是否启动');

  // 3. 一次性同时发出所有报名请求
  console.log(`正在同时发出 ${okAccounts.length} 个报名请求...`);
  const t0 = Date.now();
  const burst = await Promise.all(okAccounts.map((acc) =>
    req(`/activity-registration/limited/${PASS_ID}`, { method: 'POST', token: acc.token })
      .then((body) => ({ phone: acc.phone, token: acc.token, body }))
      .catch((e) => ({ phone: acc.phone, token: acc.token, error: e.message }))
  ));
  const burstMs = Date.now() - t0;

  // 4. 统计受理结果
  let accepted = 0, rejected = 0, transportError = 0;
  const pending = [];
  for (const r of burst) {
    if (r.error) { transportError++; continue; }
    const data = r.body.data || {};
    if (r.body.success && data.registrationId) {
      accepted++;
      pending.push({ phone: r.phone, token: r.token, registrationId: data.registrationId });
    } else {
      rejected++;
    }
  }
  console.log(`\n全部请求已返回，耗时 ${burstMs}ms`);
  console.log(`受理进入异步流程：${accepted}，被直接拒绝：${rejected}，网络/超时异常：${transportError}`);

  // 5. 轮询最终状态
  const finalStates = { SUCCESS: 0, FAILED: 0, COMPENSATED: 0, PENDING: 0, RESERVED: 0, PROCESSING: 0, TIMEOUT: 0 };
  await runPool(pending, POLL_CONCURRENCY, async (p) => {
    let status = 'PENDING';
    for (let i = 0; i < 100; i++) {
      try {
        const body = await req(`/activity-registration/${p.registrationId}/status`, { token: p.token });
        status = (body.data && body.data.status) || 'PENDING';
      } catch (e) {
        status = 'PENDING';
      }
      if (status === 'SUCCESS' || status === 'FAILED' || status === 'COMPENSATED') break;
      await sleep(500);
    }
    finalStates[status] = (finalStates[status] || 0) + 1;
    if (status === 'FAILED' || status === 'COMPENSATED') {
      const body = await req(`/activity-registration/${p.registrationId}/status`, { token: p.token });
      console.log(`[失败] phone=${p.phone} 流水=${p.registrationId} 状态=${status} 原因=${(body.data && body.data.failureReason) || ''}`);
    }
  });

  // 6. 总结
  console.log('\n================ 最终结果 ================');
  console.log('成功(SUCCESS):', finalStates.SUCCESS);
  console.log('失败(FAILED):', finalStates.FAILED);
  console.log('已补偿(COMPENSATED):', finalStates.COMPENSATED);
  console.log('仍处理中(PENDING/RESERVED/PROCESSING):', finalStates.PENDING + finalStates.RESERVED + finalStates.PROCESSING);
  console.log('超时未决(TIMEOUT):', finalStates.TIMEOUT);
  console.log('==========================================');
  console.log('判定：成功数 ≤ 名额 且 无死信 => 没超卖，系统扛住了。');
  console.log('下一步请去 MySQL/Redis 核对，SQL 见说明。');
}

main().catch((e) => { console.error('脚本出错：', e.message); process.exit(1); });
