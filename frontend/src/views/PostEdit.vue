<template>
  <div class="cp-container" style="max-width: 720px">
    <div class="cp-card">
      <div class="cp-row-between" style="margin-bottom:20px">
        <h1 style="font-size:24px; margin:0">发布校园动态</h1>
        <el-button type="primary" round :loading="submitting" @click="submit">发布</el-button>
      </div>

      <div class="upload-zone" @click="pickFile" @dragover.prevent @drop.prevent="dropFile">
        <el-icon style="font-size:34px; color:#b0acbf"><Camera /></el-icon>
        <div style="color:#8b8799; font-size:13px; margin-top:8px">点击或拖拽上传照片（支持 JPG/PNG/GIF，≤5MB）</div>
      </div>
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFile" />
      <div class="pic-grid" v-if="fileList.length">
        <div v-for="(f, i) in fileList" :key="i" class="pic-box">
          <img :src="imgUrl(f)" alt="" />
          <el-icon class="pic-del" @click="removePic(i)"><CircleCloseFilled /></el-icon>
        </div>
      </div>

      <el-input v-model="title" maxlength="80" size="large" placeholder="给你的校园故事起个标题吧" style="margin:16px 0 12px" />
      <el-input v-model="content" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="分享活动体验、校园见闻，或者推荐一个宝藏场馆吧" />

      <div class="cp-row-between" style="margin-top:16px">
        <el-button round @click="dialog = true">
          <el-icon style="margin-right:4px"><Link /></el-icon>{{ selectedActivity ? selectedActivity.name : '关联活动 / 场馆' }}
        </el-button>
        <el-button v-if="selectedActivity" text type="danger" @click="selectedActivity = null">取消关联</el-button>
      </div>

      <el-dialog v-model="dialog" title="选择关联活动" width="480px">
        <el-input v-model="keyword" placeholder="搜索活动名称" clearable @input="searchActivities" style="margin-bottom:12px" />
        <div class="cp-muted" v-if="!activities.length">输入关键词搜索活动</div>
        <div v-for="s in activities" :key="s.id" class="pick-activity" @click="choose(s)">
          <div>
            <div style="font-weight:600">{{ s.name }}</div>
            <div class="cp-muted">{{ s.area }} · {{ s.address }}</div>
          </div>
          <el-icon style="color:#b0acbf"><ArrowRight /></el-icon>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post, del, imgUrl } from '../api'

const router = useRouter()
const fileList = ref([])
const title = ref('')
const content = ref('')
const selectedActivity = ref(null)
const dialog = ref(false)
const keyword = ref('')
const activities = ref([])
const submitting = ref(false)
const fileInput = ref(null)

function pickFile() { fileInput.value.click() }
function dropFile(e) { const f = e.dataTransfer.files[0]; if (f) uploadFile(f) }

function onFile(e) {
  const f = e.target.files[0]
  if (f) uploadFile(f)
  e.target.value = ''
}

async function uploadFile(file) {
  const form = new FormData()
  form.append('file', file)
  try {
    const res = await post('/upload/post', form)
    fileList.value.push(res.data)
  } catch (e) { ElMessage.error(e.message) }
}

async function removePic(i) {
  const name = fileList.value[i].replace(/^\/imgs\//, '')
  try {
    await del('/upload/post', { name })
    fileList.value.splice(i, 1)
  } catch (e) { ElMessage.error(e.message) }
}

let searchTimer = null
function searchActivities() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    try { activities.value = (await get('/activity/search', { name: keyword.value })).data || [] } catch (e) { /* ignore */ }
  }, 300)
}
function choose(s) { selectedActivity.value = s; dialog.value = false }

async function submit() {
  if (!title.value.trim()) return ElMessage.error('请输入标题')
  if (!content.value.trim()) return ElMessage.error('请输入内容')
  submitting.value = true
  try {
    await post('/post', {
      title: title.value.trim(),
      content: content.value.trim(),
      images: fileList.value.join(','),
      activityId: selectedActivity.value ? selectedActivity.value.id : null
    })
    ElMessage.success('发布成功')
    router.push('/profile?tab=posts')
  } catch (e) { ElMessage.error(e.message) }
  finally { submitting.value = false }
}

onMounted(() => { searchActivities() })
</script>

<style scoped>
.upload-zone {
  border: 2px dashed #d6d3ec; border-radius: 14px; padding: 28px; text-align: center; cursor: pointer;
  transition: all 0.2s; background: #faf9ff;
}
.upload-zone:hover { border-color: var(--brand-1); background: #f3f2fe; }
.pic-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: 10px; margin-top: 14px; }
.pic-box { position: relative; }
.pic-box img { width: 100%; height: 110px; object-fit: cover; border-radius: 10px; }
.pic-del { position: absolute; top: -8px; right: -8px; font-size: 20px; color: #f43f5e; cursor: pointer; background:#fff; border-radius:50%; }
.pick-activity { display:flex; align-items:center; justify-content:space-between; padding:12px 4px; border-bottom:1px solid var(--line); cursor:pointer; }
.pick-activity:hover { background:#faf9ff; }
</style>

