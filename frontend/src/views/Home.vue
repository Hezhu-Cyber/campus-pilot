<template>
  <div class="cp-container">
    <section class="cp-hero">
      <h1>发现校园精彩</h1>
      <p>活动、社团、场馆与同学动态，一站式探索你的校园生活</p>
    </section>

    <section class="cp-card" style="margin-bottom: 24px">
      <h2 class="cp-section-title"><el-icon><Grid /></el-icon>活动分类</h2>
      <div class="cp-cat-grid">
        <div v-for="t in types" :key="t.id" class="cp-cat" @click="goActivities(t.id, t.name)">
          <img :src="'/imgs/' + t.icon" :alt="t.name" loading="lazy" />
          <div class="cp-cat-name">{{ t.name }}</div>
        </div>
      </div>
    </section>

    <section class="cp-card">
      <div class="cp-row-between">
        <h2 class="cp-section-title" style="margin-bottom: 0"><el-icon><HotWater /></el-icon>热门动态</h2>
        <span class="cp-muted">下拉加载更多</span>
      </div>
      <div v-if="!posts.length" class="cp-empty">还没有动态，来发布第一条吧</div>
      <div v-else>
        <div v-for="b in posts" :key="b.id" class="cp-post" @click="goPost(b.id)">
          <img v-if="cover(b)" class="cp-post-img" :src="cover(b)" alt="" loading="lazy" />
          <div class="cp-post-body">
            <h3 class="cp-post-title">{{ b.title }}</h3>
            <div class="cp-post-content">{{ b.content }}</div>
            <div class="cp-post-foot">
              <span>{{ b.name }}</span>
              <span>·</span>
              <span>{{ formatTime(b.createTime) }}</span>
              <span style="margin-left:auto; display:flex; align-items:center; gap:4px; cursor:pointer" @click.stop="toggleLike(b)">
                <el-icon :color="b.isLike ? '#6366f1' : '#8b8799'"><Pointer /></el-icon> {{ b.liked }}
              </span>
              <span style="display:flex; align-items:center; gap:4px"><el-icon><ChatDotRound /></el-icon> {{ b.comments || 0 }}</span>
            </div>
          </div>
        </div>
        <div ref="sentinel" style="height: 1px"></div>
        <div v-if="loadingMore" class="cp-empty">加载中…</div>
        <div v-else-if="noMore" class="cp-empty">— 已经到底啦 —</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, put, imgUrl } from '../api'

const router = useRouter()
const types = ref([])
const posts = ref([])
const current = ref(1)
const loadingMore = ref(false)
const noMore = ref(false)
const sentinel = ref(null)
const pageSize = 10
let observer = null

const cover = (b) => (b.images ? imgUrl(b.images.split(',')[0]) : null)

function formatTime(v) {
  if (!v) return ''
  const d = new Date(v)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
const pad = (n) => String(n).padStart(2, '0')

async function loadTypes() {
  try {
    const res = await get('/activity-category/list')
    types.value = res.data || []
  } catch (e) { ElMessage.error(e.message) }
}

async function loadPosts() {
  if (loadingMore.value || noMore.value) return
  loadingMore.value = true
  try {
    const res = await get('/post/hot', { current: current.value })
    const list = res.data || []
    posts.value = posts.value.concat(list)
    current.value += 1
    if (list.length < pageSize) noMore.value = true
  } catch (e) { ElMessage.error(e.message) }
  finally { loadingMore.value = false }
}

async function toggleLike(b) {
  try {
    const res = await put('/post/like/' + b.id)
    b.isLike = res.data
    b.liked = Math.max(0, (Number(b.liked) || 0) + (res.data ? 1 : -1))
  } catch (e) { ElMessage.error(e.message) }
}

function goActivities(id, name) { router.push({ path: '/activities', query: { type: id, name } }) }
function goPost(id) { router.push('/post/' + id) }

onMounted(async () => {
  loadTypes()
  loadPosts()
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) loadPosts()
  })
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => observer && observer.disconnect())
</script>
