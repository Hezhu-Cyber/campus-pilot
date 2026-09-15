<template>
  <div class="cp-container">
    <div class="cp-row-between" style="margin-bottom: 20px">
      <h1 style="font-size: 26px; margin: 0">{{ typeName || '全部活动' }}</h1>
      <el-select v-model="typeId" style="width: 180px" @change="switchType">
        <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
    </div>

    <div v-if="!activities.length && !loading" class="cp-card cp-empty">该分类下暂无活动</div>
    <div class="cp-grid">
      <div v-for="s in activities" :key="s.id" class="cp-card cp-activity-card" @click="goDetail(s.id)">
        <img class="cp-cover" :src="cover(s)" alt="" loading="lazy" />
        <div class="cp-card-body">
          <h3 class="cp-card-title">{{ s.name }}</h3>
          <div class="cp-card-meta"><el-icon><Location /></el-icon>{{ s.address || '地址待定' }}</div>
          <div class="cp-card-meta"><el-icon><Clock /></el-icon>{{ s.area || '本校' }} · {{ timeRange(s) }}</div>
          <div class="cp-row-between" style="margin-top: 12px">
            <span class="cp-price">{{ s.avgPrice > 0 ? '¥' + s.avgPrice : '免费' }}</span>
            <span class="cp-tag">{{ s.sold || 0 }} 人已报名</span>
          </div>
        </div>
      </div>
    </div>
    <div ref="sentinel" style="height: 1px"></div>
    <div v-if="loading" class="cp-empty">加载中…</div>
    <div v-else-if="noMore && activities.length" class="cp-empty">— 已经到底啦 —</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, imgUrl } from '../api'

const route = useRoute()
const router = useRouter()
const types = ref([])
const activities = ref([])
const typeId = ref(0)
const typeName = ref('')
const current = ref(1)
const loading = ref(false)
const noMore = ref(false)
const sentinel = ref(null)
// 与后端 SystemConstants.MAX_PAGE_SIZE 保持一致
const PAGE_SIZE = 10
let observer = null

const cover = (s) => (s.images ? imgUrl(s.images.split(',')[0]) : null)
const pad = (n) => String(n).padStart(2, '0')
const fmt = (v) => { if (!v) return ''; const d = new Date(v); return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}` }
const timeRange = (s) => s.startTime && s.endTime ? `${fmt(s.startTime)} ~ ${fmt(s.endTime)}` : (s.openHours || '时间待定')

async function loadTypes() {
  try { types.value = (await get('/activity-category/list')).data || [] } catch (e) { /* ignore */ }
}

async function loadActivities(reset) {
  if (loading.value) return
  if (reset) { activities.value = []; current.value = 1; noMore.value = false }
  if (noMore.value) return
  loading.value = true
  try {
    const res = await get('/activity/by-category', { typeId: typeId.value, current: current.value })
    const list = res.data || []
    activities.value = activities.value.concat(list)
    current.value += 1
    if (list.length < PAGE_SIZE) noMore.value = true
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function switchType(id) {
  const t = types.value.find(x => x.id === id)
  typeName.value = t ? t.name : ''
  router.replace({ query: { type: id, name: typeName.value } })
  loadActivities(true)
}

function goDetail(id) { router.push('/activity/' + id) }

onMounted(() => {
  typeId.value = Number(route.query.type) || 0
  typeName.value = route.query.name || ''
  loadTypes()
  loadActivities()
  observer = new IntersectionObserver((entries) => { if (entries[0].isIntersecting) loadActivities() })
  if (sentinel.value) observer.observe(sentinel.value)
})
onBeforeUnmount(() => observer && observer.disconnect())
</script>
