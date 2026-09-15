<template>
  <div class="cp-container" v-loading="pageLoading">
    <template v-if="activity.id">
      <div class="cp-detail-head">
        <el-image class="cp-detail-img" :src="activity.images[0]" :preview-src-list="activity.images" :initial-index="0" fit="cover" />
        <div style="flex:1; min-width:0">
          <h1 style="font-size: 28px; margin: 0 0 12px">{{ activity.name }}</h1>
          <div class="cp-card-meta" style="font-size:14px"><el-icon><Location /></el-icon>{{ activity.address }}</div>
          <div class="cp-card-meta" style="font-size:14px"><el-icon><Clock /></el-icon>{{ activity.area }} · {{ timeText }}</div>
          <p class="cp-muted" style="margin: 14px 0 0; white-space: pre-wrap">{{ activity.description }}</p>
          <div class="cp-gap" style="margin-top: 16px">
            <span class="cp-tag">容量 {{ activity.capacity || '不限' }}</span>
            <span class="cp-tag">已报名 {{ activity.sold || 0 }}</span>
            <span class="cp-tag" :style="priceTagStyle">{{ activity.avgPrice > 0 ? '报名费 ¥' + activity.avgPrice : '免费参加' }}</span>
          </div>
        </div>
      </div>

      <section class="cp-card cp-mt" v-if="passes.length">
        <h2 class="cp-section-title"><el-icon><Ticket /></el-icon>报名方式</h2>
        <div class="pass-row" v-for="v in passes" :key="v.id">
          <div style="flex:1; min-width:0">
            <div class="pass-title">{{ v.title }}</div>
            <div class="cp-muted" style="margin-top:4px">{{ v.subTitle || '' }}</div>
            <div class="cp-muted" style="margin-top:4px">
              {{ v.type === 1 ? '限量报名 · 剩余 ' + (v.stock ?? '-') + ' 个名额' : '普通报名 · 不限名额' }}
            </div>
          </div>
          <div style="text-align:right">
            <div class="cp-price" style="margin-bottom:6px">{{ v.payValue > 0 ? '¥' + v.payValue : '免费' }}</div>
            <el-button type="primary" round :loading="v.loading" :disabled="!canRegister(v)" @click="register(v)">
              {{ btnText(v) }}
            </el-button>
          </div>
        </div>
      </section>

      <section class="cp-card cp-mt">
        <div class="cp-row-between">
          <h2 class="cp-section-title" style="margin-bottom:0"><el-icon><ChatDotRound /></el-icon>相关动态（{{ posts.length }}）</h2>
        </div>
        <div v-if="!posts.length" class="cp-empty">还没有相关动态</div>
        <div v-for="b in posts" :key="b.id" class="cp-post" @click="goPost(b.id)">
          <img v-if="b.images" class="cp-post-img" :src="imgUrl(b.images.split(',')[0])" alt="" loading="lazy" />
          <div class="cp-post-body">
            <h3 class="cp-post-title">{{ b.title }}</h3>
            <div class="cp-post-content">{{ b.content }}</div>
            <div class="cp-post-foot">
              <span>{{ b.name }}</span>
              <span style="margin-left:auto; display:flex; align-items:center; gap:4px; cursor:pointer" @click.stop="toggleLike(b)">
                <el-icon :color="b.isLike ? '#6366f1' : '#8b8799'"><Pointer /></el-icon> {{ b.liked }}
              </span>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post, put, imgUrl } from '../api'

const route = useRoute()
const router = useRouter()
const activity = ref({ images: [] })
const passes = ref([])
const posts = ref([])
const pageLoading = ref(true)

const timeText = computed(() => {
  const a = activity.value
  if (a.startTime && a.endTime) return fmt(a.startTime) + ' ~ ' + fmt(a.endTime)
  return a.openHours || '时间待定'
})
const priceTagStyle = computed(() => ({ background: '#fff1f3', color: '#f43f5e' }))
const pad = (n) => String(n).padStart(2, '0')
const fmt = (v) => { const d = new Date(v); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}` }

function canRegister(v) {
  if (!v.type) return true
  if (new Date(v.beginTime).getTime() > Date.now()) return false
  if (new Date(v.endTime).getTime() < Date.now()) return false
  if (v.stock < 1) return false
  return true
}
function btnText(v) {
  if (new Date(v.beginTime).getTime() > Date.now()) return '未开始'
  if (new Date(v.endTime).getTime() < Date.now()) return '已结束'
  if (v.stock < 1) return '名额已满'
  return v.type === 1 ? '立即报名' : '免费报名'
}


async function register(v) {
  if (!localStorage.getItem('cp_token')) { ElMessage.warning('请先登录'); return router.push('/login') }
  v.loading = true
  try {
    if (v.type === 1) {
      const res = await post('/activity-registration/limited/' + v.id)
      const data = res.data
      if (!data || !data.registrationId) return ElMessage.error('报名受理结果异常，请稍后查询')
      if (data.status === 'SUCCESS') return ElMessage.success('报名成功，编号：' + data.registrationId)
      ElMessage.info('已受理，正在确认名额…')
      pollStatus(data.registrationId)
    } else {
      const res = await post('/activity-registration/' + v.id)
      ElMessage.success('报名成功，编号：' + res.data)
      refresh()
    }
  } catch (e) { ElMessage.error(e.message) }
  finally { v.loading = false }
}

function pollStatus(id, attempts = 0) {
  // 最多轮询约 30 秒，仍无结果时给出可执行的后续指引
  if (attempts >= 50) return ElMessage.warning('报名仍在处理中，请稍后刷新页面或到个人中心查看最终结果')
  setTimeout(async () => {
    try {
      const res = await get('/activity-registration/' + id + '/status')
      const s = res.data.status
      if (s === 'SUCCESS') ElMessage.success('报名成功，编号：' + id)
      else if (s === 'FAILED' || s === 'COMPENSATED') ElMessage.error(res.data.failureReason || '报名失败，名额已回补')
      else pollStatus(id, attempts + 1)
    } catch (e) { pollStatus(id, attempts + 1) }
  }, 600)
}

async function toggleLike(b) {
  try {
    const res = await put('/post/like/' + b.id)
    b.isLike = res.data
    b.liked = Math.max(0, (Number(b.liked) || 0) + (res.data ? 1 : -1))
  } catch (e) { ElMessage.error(e.message) }
}

function goPost(id) { router.push('/post/' + id) }

async function refresh() {
  const id = route.params.id
  const [a, p, ps] = await Promise.all([
    get('/activity/' + id),
    get('/registration-pass/activity/' + id),
    get('/post/by-activity', { id })
  ])
  activity.value = { ...a.data, images: (a.data.images || '').split(',').map(imgUrl) }
  passes.value = (p.data || []).map(x => ({ ...x, loading: false }))
  posts.value = ps.data || []
}

onMounted(async () => {
  try { await refresh() } catch (e) { ElMessage.error(e.message) }
  finally { pageLoading.value = false }
})
</script>

<style scoped>
.pass-row {
  display: flex; align-items: center; gap: 16px;
  padding: 16px 4px; border-bottom: 1px solid var(--line);
}
.pass-row:last-child { border-bottom: none; }
.pass-title { font-size: 17px; font-weight: 700; }
</style>

