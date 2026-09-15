<template>
  <div class="cp-assistant-root">
    <transition name="cp-assistant-pop">
      <section v-if="open" class="cp-assistant-panel" aria-label="校园智能助手">
        <header class="cp-assistant-head">
          <div class="cp-assistant-title">
            <span class="cp-assistant-logo">AI</span>
            <div>
              <strong>校园小助手</strong>
              <small>{{ modeLabel }}</small>
            </div>
          </div>
          <el-button text circle aria-label="关闭助手" @click="open = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </header>

        <div ref="scrollRef" class="cp-assistant-messages">
          <div v-for="(message, index) in messages" :key="index" class="cp-assistant-row" :class="message.role">
            <div v-if="message.role === 'assistant'" class="cp-assistant-avatar">AI</div>
            <div class="cp-assistant-bubble">
              <div class="cp-assistant-text">{{ message.text }}</div>

              <div v-if="message.activities && message.activities.length" class="cp-assistant-cards">
                <button
                  v-for="activity in message.activities"
                  :key="activity.id"
                  class="cp-assistant-activity"
                  type="button"
                  @click="goActivity(activity.id)"
                >
                  <strong>{{ activity.name }}</strong>
                  <span>{{ activity.address || activity.area || '地点待定' }}</span>
                  <span>{{ formatDate(activity.startTime) || activity.openHours || '时间待定' }}</span>
                  <em>{{ formatPrice(activity.avgPrice) }}</em>
                </button>
              </div>

              <div v-if="message.registrations && message.registrations.length" class="cp-assistant-cards">
                <button
                  v-for="item in message.registrations"
                  :key="item.id"
                  class="cp-assistant-registration"
                  type="button"
                  @click="goActivity(item.activityId)"
                >
                  <strong>{{ item.activityName || item.title }}</strong>
                  <span>{{ registrationStatus(item.status) }} · {{ item.openHours || item.address || '详情见活动页' }}</span>
                </button>
              </div>

              <div v-if="message.posts && message.posts.length" class="cp-assistant-cards">
                <div v-for="post in message.posts" :key="post.id" class="cp-assistant-post">
                  <strong>{{ post.title || '校园动态' }}</strong>
                  <span>{{ post.content || '点击进入校园社区查看详情' }}</span>
                  <em>{{ post.liked || 0 }} 个赞 · {{ post.comments || 0 }} 条评论</em>
                </div>
              </div>

              <div v-if="message.pendingAction" class="cp-assistant-action">
                <strong>{{ message.pendingAction.summary }}</strong>
                <span>确认后才会执行，确认令牌 5 分钟内有效</span>
                <div class="cp-assistant-action-buttons">
                  <el-button type="primary" size="small" :loading="message.actionLoading" @click="confirmPendingAction(message)">确认</el-button>
                  <el-button size="small" :disabled="message.actionLoading" @click="dismissPendingAction(message)">暂不</el-button>
                </div>
              </div>
            </div>
          </div>
          <div v-if="sending" class="cp-assistant-row assistant">
            <div class="cp-assistant-avatar">AI</div>
            <div class="cp-assistant-typing"><span></span><span></span><span></span></div>
          </div>
        </div>

        <div v-if="suggestions.length && !sending" class="cp-assistant-suggestions">
          <button v-for="item in suggestions" :key="item" type="button" @click="send(item)">
            {{ item }}
          </button>
        </div>

        <footer class="cp-assistant-input">
          <textarea
            v-model="input"
            rows="1"
            maxlength="1000"
            placeholder="问活动、报名记录或校园推荐..."
            @keydown.enter.exact.prevent="send()"
          ></textarea>
          <el-button type="primary" circle :loading="sending" @click="send()">
            <el-icon><Promotion /></el-icon>
          </el-button>
        </footer>
      </section>
    </transition>

    <button
      class="cp-assistant-toggle"
      :class="{ active: open }"
      type="button"
      aria-label="打开校园智能助手"
      @click="togglePanel"
    >
      <el-icon><ChatDotRound /></el-icon>
      <span>智能助手</span>
    </button>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post } from '../api'

const route = useRoute()
const router = useRouter()
const open = ref(false)
const sending = ref(false)
const input = ref('')
const mode = ref('rules')
const scrollRef = ref(null)
const suggestions = ref(['本周有什么活动？', '帮我报名新生篮球友谊赛', '取消报名', '我的报名记录'])
const messages = ref([{
  role: 'assistant',
  text: '你好，我是校园小助手。我可以找活动、查看报名记录，并在我先给出确认卡、你点击确认后帮你报名或取消报名。',
  activities: [],
  registrations: [],
  pendingAction: null,
}])

const modeLabel = ref('正在为你整理校园信息')

function togglePanel() {
  open.value = !open.value
  if (open.value) nextTick(scrollToBottom)
}

function threadId() {
  const user = JSON.parse(localStorage.getItem('cp_user') || 'null')
  const key = `cp_assistant_thread_${user && user.id ? user.id : 'guest'}`
  let value = localStorage.getItem(key)
  if (!value) {
    value = (crypto.randomUUID && crypto.randomUUID()) || `web-${Date.now()}-${Math.random()}`
    localStorage.setItem(key, value)
  }
  return value
}

async function send(preset) {
  const text = String(preset || input.value || '').trim()
  if (!text || sending.value) return
  if (!localStorage.getItem('cp_token')) {
    open.value = false
    router.push('/login')
    return
  }

  messages.value.push({ role: 'user', text, activities: [], registrations: [], posts: [], pendingAction: null })
  input.value = ''
  suggestions.value = []
  sending.value = true
  await nextTick(scrollToBottom)

  try {
    const res = await post('/assistant/chat', {
      threadId: threadId(),
      message: text,
      pagePath: route.fullPath,
    }, { timeout: 60000 })
    const data = res.data || {}
    mode.value = data.mode || 'rules'
    modeLabel.value = mode.value === 'llm' ? '由大模型实时生成' : (mode.value === 'degraded' ? '降级安全模式' : '本地规则模式')
    messages.value.push({
      role: 'assistant',
      text: data.answer || '暂时没有生成回答，请稍后再试。',
      activities: data.activities || [],
      registrations: data.registrations || [],
      pendingAction: data.pendingAction || null,
    })
    suggestions.value = data.suggestedQuestions || []
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      text: `暂时无法连接智能助手。${error.message || '请稍后再试。'}`,
      activities: [],
      registrations: [],
      pendingAction: null,
    })
    suggestions.value = ['本周有什么活动？', '我的报名记录']
  } finally {
    sending.value = false
    await nextTick(scrollToBottom)
  }
}

async function confirmPendingAction(message) {
  const action = message.pendingAction
  if (!action || !action.confirmationToken || message.actionLoading) return
  message.actionLoading = true
  try {
    const res = await post('/assistant/actions/confirm', {
      token: action.confirmationToken,
    }, { timeout: 60000 })
    applyActionResult(message, res.data || {})
  } catch (error) {
    message.text += `\n操作未完成：${error.message || '请稍后重试'}`
    ElMessage.error(error.message || '助手操作失败')
  } finally {
    message.actionLoading = false
    await nextTick(scrollToBottom)
  }
}

function dismissPendingAction(message) {
  message.pendingAction = null
  message.actionLoading = false
  message.text += '\n已取消本次操作。'
}

function applyActionResult(message, data, schedulePoll = true) {
  const status = data.status || 'PROCESSING'
  const statusKey = `${status}:${data.retryable ? 'retryable' : 'final'}`
  if (message.lastActionStatus !== statusKey && data.message) {
    message.text += `\n${data.message}`
    message.lastActionStatus = statusKey
  }

  if (status === 'SUCCESS' || status === 'CANCELLED') {
    message.pendingAction = null
    ElMessage.success(data.message || (status === 'CANCELLED' ? '报名已取消' : '报名成功'))
    return
  }
  if (status === 'FAILED' || status === 'EXPIRED' || status === 'COMPENSATED') {
    message.pendingAction = null
    ElMessage.error(data.message || '操作未完成')
    return
  }
  if (data.retryable || status === 'FAILED_RETRYABLE') return
  if (schedulePoll && (data.requiresPolling || status === 'PROCESSING')) {
    const actionId = data.actionId || (message.pendingAction && message.pendingAction.actionId)
    if (actionId) pollAssistantActionStatus(message, actionId)
  }
}

function pollAssistantActionStatus(message, actionId, attempts = 0) {
  if (attempts >= 60) {
    message.text += '\n操作仍在处理中，可稍后重新点击确认查询结果。'
    return
  }
  setTimeout(async () => {
    try {
      const res = await post('/assistant/actions/status', { token: actionId })
      const data = res.data || {}
      applyActionResult(message, data, false)
      const status = data.status || 'PROCESSING'
      if (status === 'PROCESSING' && !data.retryable) {
        pollAssistantActionStatus(message, actionId, attempts + 1)
      }
    } catch (error) {
      pollAssistantActionStatus(message, actionId, attempts + 1)
    }
  }, 1000)
}

function scrollToBottom() {
  if (scrollRef.value) {
    scrollRef.value.scrollTo({ top: scrollRef.value.scrollHeight, behavior: 'smooth' })
  }
}

function goActivity(id) {
  if (!id) return
  open.value = false
  router.push(`/activity/${id}`)
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatPrice(value) {
  const price = Number(value)
  if (!Number.isFinite(price) || price <= 0) return '免费'
  return `${price} 元`
}

function registrationStatus(value) {
  const status = Number(value)
  if (status === 1) return '已报名'
  if (status === 2) return '已完成'
  if (status === 4) return '已取消'
  return '处理中'
}
</script>

<style scoped>
.cp-assistant-root { position: fixed; right: 24px; bottom: 24px; z-index: 1200; }
.cp-assistant-toggle {
  display: flex; align-items: center; gap: 8px; margin-left: auto;
  border: 0; border-radius: 999px; padding: 13px 18px; color: #fff; cursor: pointer;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 14px 32px rgba(99, 102, 241, 0.34);
  font-size: 14px; font-weight: 700; transition: transform 0.18s, box-shadow 0.18s;
}
.cp-assistant-toggle:hover { transform: translateY(-2px); box-shadow: 0 18px 40px rgba(99, 102, 241, 0.42); }
.cp-assistant-toggle.active { transform: scale(0.96); }
.cp-assistant-panel {
  width: min(410px, calc(100vw - 32px)); height: min(650px, calc(100vh - 120px));
  margin-bottom: 14px; display: flex; flex-direction: column; overflow: hidden;
  background: rgba(255,255,255,0.98); border: 1px solid #e8e6f2; border-radius: 22px;
  box-shadow: 0 24px 70px rgba(36, 31, 74, 0.22); backdrop-filter: blur(16px);
}
.cp-assistant-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 18px; border-bottom: 1px solid #eeecf6;
  background: linear-gradient(135deg, rgba(99,102,241,0.08), rgba(139,92,246,0.08));
}
.cp-assistant-title { display: flex; align-items: center; gap: 10px; }
.cp-assistant-title strong { display: block; font-size: 15px; color: #24203a; }
.cp-assistant-title small { display: block; margin-top: 2px; color: #8b8799; font-size: 11px; }
.cp-assistant-logo, .cp-assistant-avatar {
  display: grid; place-items: center; flex: 0 0 auto; color: #fff; font-weight: 800;
  background: linear-gradient(135deg, #6366f1, #a855f7);
}
.cp-assistant-logo { width: 36px; height: 36px; border-radius: 12px; font-size: 12px; }
.cp-assistant-avatar { width: 28px; height: 28px; border-radius: 10px; font-size: 10px; }
.cp-assistant-messages { flex: 1; overflow-y: auto; padding: 18px; background: #faf9ff; }
.cp-assistant-row { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 14px; }
.cp-assistant-row.user { justify-content: flex-end; }
.cp-assistant-bubble {
  max-width: 86%; padding: 11px 13px; border-radius: 15px; color: #3e3957; font-size: 13px;
  line-height: 1.65; background: #fff; border: 1px solid #eceaf5; box-shadow: 0 5px 18px rgba(64, 55, 120, 0.06);
}
.cp-assistant-row.user .cp-assistant-bubble {
  color: #fff; background: linear-gradient(135deg, #6366f1, #8b5cf6); border-color: transparent;
}
.cp-assistant-text { white-space: pre-wrap; word-break: break-word; }
.cp-assistant-cards { display: grid; gap: 8px; margin-top: 10px; }
.cp-assistant-activity, .cp-assistant-registration {
  display: flex; flex-direction: column; gap: 4px; width: 100%; padding: 10px 11px;
  text-align: left; cursor: pointer; border: 1px solid #e8e5f5; border-radius: 12px;
  background: #f8f7ff; color: #5d5775; font-size: 12px; transition: all 0.16s;
}
.cp-assistant-post { display: flex; flex-direction: column; gap: 4px; padding: 10px 11px; border: 1px solid #e8e5f5; border-radius: 12px; background: #f8f7ff; color: #5d5775; font-size: 12px; }
.cp-assistant-post strong { color: #2e2948; font-size: 13px; }
.cp-assistant-post em { color: #8b8799; font-style: normal; }
.cp-assistant-activity:hover, .cp-assistant-registration:hover { border-color: #a5a6f6; background: #f0efff; }
.cp-assistant-activity strong, .cp-assistant-registration strong { color: #2e2948; font-size: 13px; }
.cp-assistant-activity em { color: #ef476f; font-style: normal; font-weight: 700; }
.cp-assistant-typing {
  display: flex; gap: 4px; align-items: center; padding: 12px 14px;
  border: 1px solid #eceaf5; border-radius: 15px; background: #fff;
}
.cp-assistant-typing span {
  width: 6px; height: 6px; border-radius: 50%; background: #8b8cf6; animation: cp-assistant-bounce 1s infinite;
}
.cp-assistant-typing span:nth-child(2) { animation-delay: 0.15s; }
.cp-assistant-typing span:nth-child(3) { animation-delay: 0.3s; }
.cp-assistant-suggestions { display: flex; gap: 7px; overflow-x: auto; padding: 10px 14px 0; }
.cp-assistant-suggestions button {
  flex: 0 0 auto; padding: 7px 10px; border: 1px solid #dedcf2; border-radius: 999px;
  color: #625d7a; background: #fff; cursor: pointer; font-size: 12px;
}
.cp-assistant-suggestions button:hover { color: #5d5ff0; border-color: #a5a6f6; background: #f5f4ff; }
.cp-assistant-input { display: flex; gap: 10px; align-items: flex-end; padding: 14px; border-top: 1px solid #eeecf6; background: #fff; }
.cp-assistant-input textarea {
  flex: 1; min-height: 40px; max-height: 96px; resize: none; padding: 10px 12px;
  border: 1px solid #e2dff0; border-radius: 12px; outline: none; color: #342f4c; font: inherit; font-size: 13px;
}
.cp-assistant-input textarea:focus { border-color: #8b8cf6; box-shadow: 0 0 0 3px rgba(99,102,241,0.08); }
.cp-assistant-pop-enter-active, .cp-assistant-pop-leave-active { transition: opacity 0.18s, transform 0.18s; transform-origin: right bottom; }
.cp-assistant-pop-enter-from, .cp-assistant-pop-leave-to { opacity: 0; transform: translateY(12px) scale(0.97); }
.cp-assistant-action { margin-top: 10px; padding: 11px; border: 1px solid #cfd0fb; border-radius: 12px; background: #f2f1ff; }
.cp-assistant-action strong { display: block; color: #2e2948; font-size: 13px; line-height: 1.5; }
.cp-assistant-action > span { display: block; margin-top: 5px; color: #8b8799; font-size: 11px; }
.cp-assistant-action-buttons { display: flex; gap: 8px; margin-top: 10px; }

@keyframes cp-assistant-bounce { 0%, 60%, 100% { transform: translateY(0); opacity: 0.55; } 30% { transform: translateY(-4px); opacity: 1; } }
@media (max-width: 640px) {
  .cp-assistant-root { right: 14px; bottom: 14px; }
  .cp-assistant-toggle span { display: none; }
  .cp-assistant-toggle { padding: 14px; }
  .cp-assistant-panel { position: fixed; inset: 72px 12px 78px; width: auto; height: auto; margin: 0; border-radius: 20px; }
}
</style>

