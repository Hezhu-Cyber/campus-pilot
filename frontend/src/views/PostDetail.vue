<template>
  <div class="cp-container" style="max-width: 860px" v-loading="loading">
    <template v-if="post.id">
      <div class="cp-card" style="margin-bottom:20px">
        <div class="cp-post-author" @click="$router.push('/user/' + post.userId)">
          <el-avatar :size="46" :src="post.icon || '/imgs/icons/default-icon.png'" />
          <div>
            <div style="font-weight:700">{{ post.name }}</div>
            <div class="cp-muted">{{ formatTime(post.createTime) }}</div>
          </div>
        </div>
        <h1 style="font-size:24px; margin:18px 0 10px">{{ post.title }}</h1>
        <div class="cp-gallery" v-if="post.images && post.images.length">
          <el-image v-for="(img, i) in post.images" :key="i" :src="img" :preview-src-list="post.images" :initial-index="i" fit="cover" class="cp-gallery-img" loading="lazy" />
        </div>
        <p style="font-size:15px; line-height:1.8; white-space:pre-wrap; color:#333">{{ post.content }}</p>

        <div v-if="activity && activity.id" class="cp-related" @click="$router.push('/activity/' + activity.id)">
          <img class="cp-related-img" :src="imgUrl((activity.images || '').split(',')[0])" alt="" />
          <div style="flex:1; min-width:0">
            <div style="font-weight:700">{{ activity.name }}</div>
            <div class="cp-muted" style="margin-top:4px">{{ activity.avgPrice > 0 ? '报名费 ¥' + activity.avgPrice : '免费参加' }} · {{ activity.address }}</div>
          </div>
          <el-icon style="color:#b0acbf"><ArrowRight /></el-icon>
        </div>

        <div class="cp-like-bar">
          <el-button :type="post.isLike ? 'primary' : 'default'" round plain @click="toggleLike">
            <el-icon style="margin-right:4px"><Pointer /></el-icon>{{ post.liked }} 人点赞
          </el-button>
          <div class="cp-likers">
            <el-tooltip v-for="u in likes" :key="u.id" :content="u.nickName || '同学'">
              <el-avatar :size="28" :src="u.icon || '/imgs/icons/default-icon.png'" style="margin-left:-6px; border:2px solid #fff" />
            </el-tooltip>
          </div>
        </div>
      </div>

      <div class="cp-card">
        <h2 class="cp-section-title"><el-icon><ChatDotRound /></el-icon>同学讨论（{{ commentTotal }}）</h2>
        <div style="display:flex; gap:10px; margin-bottom:16px">
          <el-input v-model="commentText" maxlength="500" placeholder="友善地参与讨论…" @keyup.enter="submitComment" />
          <el-button type="primary" round @click="submitComment">发送</el-button>
        </div>
        <div v-if="!comments.length" class="cp-empty">还没有讨论，来发表第一条吧</div>
        <div v-for="c in comments" :key="c.id" class="cp-comment">
          <el-avatar :size="38" :src="c.userIcon || '/imgs/icons/default-icon.png'" />
          <div style="flex:1; min-width:0">
            <div class="cp-muted">{{ c.userName || '校园同学' }} · {{ formatTime(c.createTime) }}</div>
            <div style="margin-top:6px; white-space:pre-wrap; line-height:1.7">{{ c.content }}</div>
          </div>
        </div>
        <el-button v-if="comments.length && !commentsDone" text type="primary" style="width:100%; margin-top:8px" @click="loadComments()">加载更多</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post as apiPost, put, imgUrl } from '../api'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const post = ref({ images: [] })
const activity = ref(null)
const likes = ref([])
const comments = ref([])
const commentTotal = ref(0)
const commentText = ref('')
const commentCurrent = ref(1)
const commentsDone = ref(false)

const pad = (n) => String(n).padStart(2, '0')
const formatTime = (v) => { if (!v) return ''; const d = new Date(v); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}` }


async function loadPost() {
  const res = await get('/post/' + route.params.id)
  post.value = { ...res.data, images: (res.data.images || '').split(',').map(imgUrl) }
  if (post.value.activityId) {
    activity.value = (await get('/activity/' + post.value.activityId)).data
  }
  likes.value = (await get('/post/likes/' + post.value.id)).data || []
}

async function loadComments() {
  const res = await get('/post-comments/post/' + route.params.id, { current: commentCurrent.value })
  const list = res.data || []
  comments.value = comments.value.concat(list)
  commentTotal.value = res.total || list.length
  commentCurrent.value += 1
  if (list.length < 10) commentsDone.value = true
}

async function submitComment() {
  if (!commentText.value.trim()) return ElMessage.error('请输入评论内容')
  try {
    await apiPost('/post-comments/post/' + post.value.id, { content: commentText.value.trim() })
    commentText.value = ''
    comments.value = []; commentCurrent.value = 1; commentsDone.value = false
    ElMessage.success('评论成功')
    loadComments()
  } catch (e) { ElMessage.error(e.message) }
}

async function toggleLike() {
  try {
    const res = await put('/post/like/' + post.value.id)
    post.value.isLike = res.data
    post.value.liked = Math.max(0, (Number(post.value.liked) || 0) + (res.data ? 1 : -1))
    likes.value = (await get('/post/likes/' + post.value.id)).data || []
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => {
  try { await loadPost(); await loadComments() } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
})
</script>

<style scoped>
.cp-post-author { display:flex; align-items:center; gap:12px; cursor:pointer; width:fit-content; }
.cp-gallery { display:grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap:10px; margin: 14px 0; }
.cp-gallery-img { width:100%; height:200px; object-fit:cover; border-radius:12px; cursor:pointer; }
.cp-related { display:flex; align-items:center; gap:14px; background:#f8f7fe; border-radius:14px; padding:12px; cursor:pointer; margin: 14px 0; }
.cp-related-img { width:88px; height:64px; object-fit:cover; border-radius:10px; }
.cp-like-bar { display:flex; align-items:center; gap:14px; margin-top:16px; }
.cp-likers { display:flex; }
.cp-comment { display:flex; gap:12px; padding:14px 0; border-top:1px solid var(--line); }
</style>


