<template>
  <div class="login-page">
    <div class="login-shell">
      <div class="login-brand">
        <span class="login-logo">🎓</span>
        <h1>校园派</h1>
        <p>发现校园精彩 · 一站式校园活动平台</p>
      </div>
      <div class="login-card">
        <div class="role-switch">
          <button v-for="r in roles" :key="r.value" class="role-btn" :class="{ active: role === r.value }" @click="role = r.value">
            {{ r.label }}
          </button>
        </div>
        <p class="role-hint">{{ roleHint }}</p>

        <el-tabs v-model="mode" stretch>
          <el-tab-pane label="验证码登录" name="code">
            <el-input v-model="phone" placeholder="请输入手机号" size="large" clearable>
              <template #prefix><el-icon><Iphone /></el-icon></template>
            </el-input>
            <div style="display:flex; gap:10px; margin-top:14px">
              <el-input v-model="code" placeholder="验证码" size="large" clearable>
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
              <el-button size="large" :disabled="counting" @click="sendCode" style="width:130px">
                {{ counting ? countdown + 's' : '发送验证码' }}
              </el-button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="密码登录" name="password">
            <el-input v-model="phone" placeholder="请输入手机号" size="large" clearable>
              <template #prefix><el-icon><Iphone /></el-icon></template>
            </el-input>
            <el-input v-model="password" type="password" show-password placeholder="请输入密码" size="large" style="margin-top:14px">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-tab-pane>
        </el-tabs>

        <el-checkbox v-model="agreed" style="margin: 18px 0 14px">
          我已阅读并同意《用户服务协议》与《隐私政策》
        </el-checkbox>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="login">登 录</el-button>
        <p class="login-tip">未注册的手机号验证通过后将自动创建账号</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post, get } from '../api'

const router = useRouter()
const route = useRoute()
const roles = [
  { value: 'STUDENT', label: '学生' },
  { value: 'ORGANIZER', label: '组织者' },
  { value: 'ADMIN', label: '管理员' }
]
const roleNames = { STUDENT: '学生', ORGANIZER: '活动组织者', ADMIN: '管理员' }

const role = ref('STUDENT')
const mode = ref('code')
const phone = ref('')
const code = ref('')
const password = ref('')
const agreed = ref(true)
const loading = ref(false)
const counting = ref(false)
const countdown = ref(60)
let timer = null

const roleHint = computed(() => {
  if (role.value === 'ORGANIZER') return '组织者登录后可进入活动发布与管理工作台'
  if (role.value === 'ADMIN') return '管理员登录后可进入系统管理工作台'
  return '首次使用手机号验证码登录会自动创建学生账号'
})

async function sendCode() {
  if (!/^1\d{10}$/.test(phone.value)) return ElMessage.error('请输入正确的手机号')
  try {
    const res = await post('/user/code?phone=' + phone.value)
    ElMessage.success(res.data ? '演示验证码：' + res.data : '验证码已发送')
    counting.value = true
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) { clearInterval(timer); counting.value = false }
    }, 1000)
  } catch (e) { ElMessage.error(e.message) }
}

async function login() {
  if (!agreed.value) return ElMessage.error('请先确认阅读用户协议')
  if (!/^1\d{10}$/.test(phone.value)) return ElMessage.error('请输入正确的手机号')
  const body = { phone: phone.value }
  if (mode.value === 'code') {
    if (!code.value) return ElMessage.error('请输入验证码')
    body.code = code.value
  } else {
    if (!password.value) return ElMessage.error('请输入密码')
    body.password = password.value
  }
  loading.value = true
  try {
    const res = await post('/user/login', body)
    localStorage.setItem('cp_token', res.data)
    const me = await get('/user/me')
    const user = me.data
    if (user.role !== role.value) {
      await post('/user/logout').catch(() => {})
      localStorage.removeItem('cp_token')
      ElMessage.error(`该账号的实际身份是「${roleNames[user.role] || user.role}」，请切换正确入口登录`)
      return
    }
    localStorage.setItem('cp_user', JSON.stringify(user))
    ElMessage.success('登录成功，欢迎回来！')
    const redirect = route.query.redirect
    router.push(redirect || (user.role === 'ADMIN' ? '/admin' : user.role === 'ORGANIZER' ? '/organizer' : '/'))
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

onBeforeUnmount(() => timer && clearInterval(timer))
</script>

<style scoped>
.login-page {
  min-height: 100vh; display: grid; place-items: center; padding: 40px 20px;
  background: linear-gradient(135deg, #eef1ff 0%, #f6f0ff 50%, #fdf2f8 100%);
}
.login-shell { width: 100%; max-width: 420px; }
.login-brand { text-align: center; margin-bottom: 28px; }
.login-logo {
  width: 64px; height: 64px; border-radius: 18px; display: inline-grid; place-items: center;
  font-size: 34px; background: linear-gradient(135deg, #6366f1, #a855f7);
  box-shadow: 0 12px 30px rgba(99,102,241,0.35);
}
.login-brand h1 { margin: 14px 0 6px; font-size: 28px; }
.login-brand p { margin: 0; color: #8b8799; font-size: 14px; }
.login-card {
  background: #fff; border-radius: 20px; padding: 28px;
  box-shadow: 0 20px 60px rgba(99,102,241,0.14); border: 1px solid #eeeafc;
}
.role-switch { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; background: #f3f2fb; padding: 6px; border-radius: 14px; }
.role-btn {
  border: none; background: transparent; padding: 9px 0; border-radius: 10px;
  font-size: 14px; color: #8b8799; cursor: pointer; transition: all 0.18s; font-weight: 600;
}
.role-btn.active { background: #fff; color: #6366f1; box-shadow: 0 4px 12px rgba(99,102,241,0.18); }
.role-hint { font-size: 12px; color: #8b8799; text-align: center; margin: 12px 0 18px; }
.login-tip { text-align: center; color: #b0acbf; font-size: 12px; margin-top: 16px; }
</style>
