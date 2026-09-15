<template>
  <div class="cp-container">
    <div class="cp-card" style="display:flex; align-items:center; gap:20px; margin-bottom:24px">
      <el-avatar :size="72" :src="user.icon || '/imgs/icons/default-icon.png'" />
      <div style="flex:1">
        <div style="font-size:22px; font-weight:800">{{ user.nickName }}</div>
        <div class="cp-muted" style="margin-top:6px">
          <el-tag size="small" round effect="light" :type="roleTag">{{ roleName }}</el-tag>
          <span style="margin-left:8px">{{ user.city ? '📍 ' + user.city : '校园认证用户' }}</span>
        </div>
        <p v-if="userInfo.introduce" class="cp-muted" style="margin:10px 0 0">{{ userInfo.introduce }}</p>
      </div>
      <div class="cp-gap">
        <el-button v-if="isStaff" round @click="$router.push(user.role === 'ADMIN' ? '/admin' : '/organizer')">
          <el-icon style="margin-right:4px"><Suitcase /></el-icon>{{ user.role === 'ADMIN' ? '管理后台' : '组织者工作台' }}
        </el-button>
        <el-button round @click="$router.push('/profile/edit')"><el-icon style="margin-right:4px"><Edit /></el-icon>编辑资料</el-button>
        <el-button round type="danger" plain @click="logout">退出</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="我的动态" name="posts">
        <div class="cp-card">
          <div v-if="!posts.length" class="cp-empty">还没有发布过动态</div>
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
          <el-button v-if="posts.length && !postsDone" text type="primary" style="width:100%" @click="loadPosts()">加载更多</el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="我的报名" name="registrations">
        <div class="cp-card">
          <div v-if="!registrations.length" class="cp-empty">还没有活动报名，去发现页逛逛吧</div>
          <div v-for="r in registrations" :key="r.id" class="cp-post" @click="r.activityId && $router.push('/activity/' + r.activityId)">
            <div class="cp-post-body">
              <h3 class="cp-post-title">{{ r.activityName || r.title || '校园活动' }}</h3>
              <div class="cp-muted" style="margin-top:6px">{{ r.address }} · {{ r.openHours }}</div>
              <div class="cp-post-foot">
                <span>编号：{{ r.id }}</span>
                <el-tag size="small" :type="r.status === 4 ? 'info' : 'success'">{{ r.status === 4 ? '已取消' : '报名成功' }}</el-tag>
                <span style="margin-left:auto">{{ formatTime(r.createTime) }}</span>
              </div>
            </div>
            <el-button v-if="r.status !== 4" type="danger" plain size="small" @click.stop="cancel(r)">取消报名</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, del, post, imgUrl } from '../api'

const route = useRoute()
const router = useRouter()
const user = ref({})
const userInfo = ref({})
const posts = ref([])
const registrations = ref([])
const postCurrent = ref(1)
const postsDone = ref(false)
const tab = ref(route.query.tab === 'registrations' ? 'registrations' : 'posts')

const roleNames = { STUDENT: '学生', ORGANIZER: '活动组织者', ADMIN: '管理员' }
const roleName = computed(() => roleNames[user.value.role] || '学生')
const roleTag = computed(() => user.value.role === 'ADMIN' ? 'danger' : user.value.role === 'ORGANIZER' ? 'warning' : 'primary')
const isStaff = computed(() => ['ORGANIZER', 'ADMIN'].includes(user.value.role))
const pad = (n) => String(n).padStart(2, '0')
const formatTime = (v) => { if (!v) return ''; const d = new Date(v); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}` }

async function loadUser() {
  try {
    user.value = (await get('/user/me')).data
    localStorage.setItem('cp_user', JSON.stringify(user.value))
    const info = await get('/user/info/' + user.value.id)
    userInfo.value = info.data || {}
  } catch (e) { ElMessage.error(e.message) }
}

async function loadPosts() {
  try {
    const res = await get('/post/mine', { current: postCurrent.value })
    const list = res.data || []
    posts.value = posts.value.concat(list)
    postCurrent.value += 1
    if (list.length < 10) postsDone.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function loadRegistrations() {
  try { registrations.value = (await get('/activity-registration/mine')).data || [] } catch (e) { ElMessage.error(e.message) }
}

function onTab() {
  if (tab.value === 'posts' && !posts.value.length) loadPosts()
  if (tab.value === 'registrations' && !registrations.value.length) loadRegistrations()
}

async function cancel(r) {
  try {
    await ElMessageBox.confirm(`确认取消「${r.activityName || r.title || '该活动'}」的报名吗？`, '取消报名', { type: 'warning' })
    await del('/activity-registration/' + r.id)
    ElMessage.success('已取消报名')
    loadRegistrations()
  } catch (e) { if (e !== 'cancel' && e.message) ElMessage.error(e.message) }
}

async function logout() {
  try { await post('/user/logout') } catch (e) { /* ignore */ }
  localStorage.removeItem('cp_token'); localStorage.removeItem('cp_user')
  ElMessage.success('已退出登录')
  router.push('/login')
}

watch(() => route.query.tab, (v) => { tab.value = v === 'registrations' ? 'registrations' : 'posts'; onTab() })

onMounted(() => { loadUser(); onTab() })
</script>
