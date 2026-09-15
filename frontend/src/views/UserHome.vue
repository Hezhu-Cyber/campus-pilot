<template>
  <div class="cp-container" style="max-width: 820px">
    <div class="cp-card" style="display:flex; align-items:center; gap:20px; margin-bottom:24px">
      <el-avatar :size="72" :src="user.icon || '/imgs/icons/default-icon.png'" />
      <div>
        <div style="font-size:22px; font-weight:800">{{ user.nickName }}</div>
        <div class="cp-muted" style="margin-top:6px">校园认证用户 · {{ roleNames[user.role] || '同学' }}</div>
        <p v-if="info.introduce" class="cp-muted" style="margin:10px 0 0">{{ info.introduce }}</p>
        <p v-else class="cp-muted" style="margin:10px 0 0">这个人很懒，什么都没有留下</p>
      </div>
    </div>
    <div class="cp-card">
      <h2 class="cp-section-title"><el-icon><Notebook /></el-icon>Ta 的动态</h2>
      <div v-if="!posts.length" class="cp-empty">Ta 还没有发布动态</div>
      <div v-for="b in posts" :key="b.id" class="cp-post" @click="$router.push('/post/' + b.id)">
        <img v-if="b.images" class="cp-post-img" :src="imgUrl(b.images.split(',')[0])" alt="" loading="lazy" />
        <div class="cp-post-body">
          <h3 class="cp-post-title">{{ b.title }}</h3>
          <div class="cp-post-content">{{ b.content }}</div>
          <div class="cp-post-foot">
            <span>{{ formatTime(b.createTime) }}</span>
            <span style="margin-left:auto">👍 {{ b.liked }} · 💬 {{ b.comments || 0 }}</span>
          </div>
        </div>
      </div>
      <el-button v-if="posts.length && !done" text type="primary" style="width:100%" @click="loadPosts()">加载更多</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, imgUrl } from '../api'

const route = useRoute()
const user = ref({})
const info = ref({})
const posts = ref([])
const current = ref(1)
const done = ref(false)
const roleNames = { STUDENT: '学生', ORGANIZER: '活动组织者', ADMIN: '管理员' }
const pad = (n) => String(n).padStart(2, '0')
const formatTime = (v) => { if (!v) return ''; const d = new Date(v); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}` }

async function loadPosts() {
  try {
    const res = await get('/post/by-user', { id: route.params.id, current: current.value })
    const list = res.data || []
    posts.value = posts.value.concat(list)
    current.value += 1
    if (list.length < 10) done.value = true
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => {
  try {
    user.value = (await get('/user/' + route.params.id)).data || {}
    const infoRes = await get('/user/info/' + route.params.id)
    info.value = infoRes.data || {}
    loadPosts()
  } catch (e) { ElMessage.error(e.message) }
})
</script>
