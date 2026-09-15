<template>
  <div class="cp-container">
    <div class="cp-hero" style="margin-bottom:24px">
      <h1>活动组织者工作台</h1>
      <p>创建和管理你发布的活动，活动将自动绑定你的账号</p>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="创建活动" name="create">
        <div class="cp-card" style="max-width: 780px">
          <el-form :model="form" label-position="top" ref="formRef" :rules="rules">
            <div style="display:grid; grid-template-columns:1fr 1fr; gap:0 20px">
              <el-form-item label="活动名称" prop="name">
                <el-input v-model="form.name" maxlength="128" show-word-limit />
              </el-form-item>
              <el-form-item label="活动分类" prop="typeId">
                <el-select v-model="form.typeId" placeholder="请选择分类" style="width:100%">
                  <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="所在校区 / 区域">
                <el-input v-model="form.area" placeholder="例如：东校区" />
              </el-form-item>
              <el-form-item label="详细地址" prop="address">
                <el-input v-model="form.address" placeholder="例如：大学生活动中心 201" />
              </el-form-item>
              <el-form-item label="开始时间">
                <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
              </el-form-item>
              <el-form-item label="结束时间">
                <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
              </el-form-item>
              <el-form-item label="报名截止时间">
                <el-date-picker v-model="form.registrationDeadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
              </el-form-item>
              <el-form-item label="开放时间说明">
                <el-input v-model="form.openHours" placeholder="例如：周六 14:00-17:00" />
              </el-form-item>
              <el-form-item label="活动容量">
                <el-input-number v-model="form.capacity" :min="1" :max="100000" style="width:100%" />
              </el-form-item>
              <el-form-item label="报名费用（元）">
                <el-input-number v-model="form.avgPrice" :min="0" :max="100000" style="width:100%" />
              </el-form-item>
            </div>
            <el-form-item label="活动介绍" class="full">
              <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit />
            </el-form-item>
            <el-form-item label="活动封面图片地址" class="full" prop="images">
              <el-input v-model="form.images" placeholder="例如 /imgs/campus/lecture-banner.svg" />
              <div class="cp-gap" style="margin-top:10px">
                <img v-for="p in presetCovers" :key="p" class="cover-opt" :class="{active: form.images === p}" :src="p" @click="form.images = p" />
              </div>
            </el-form-item>
            <div class="cp-gap">
              <el-button type="primary" round :loading="submitting" @click="submit">创建活动</el-button>
              <el-button round @click="reset">重置</el-button>
            </div>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="`我的活动（${activities.length}）`" name="mine">
        <div v-if="!activities.length" class="cp-card cp-empty">还没有创建活动，切换到「创建活动」发布第一个吧</div>
        <div class="cp-grid">
          <div v-for="item in activities" :key="item.id" class="cp-card cp-activity-card" @click="$router.push('/activity/' + item.id)">
            <img class="cp-cover" :src="cover(item)" alt="" loading="lazy" />
            <div class="cp-card-body">
              <h3 class="cp-card-title">{{ item.name }}</h3>
              <div class="cp-card-meta"><el-icon><Location /></el-icon>{{ item.address }}</div>
              <div class="cp-row-between" style="margin-top:10px">
                <span class="cp-muted">已报名 {{ item.sold || 0 }} / {{ item.capacity || '不限' }}</span>
                <span class="cp-tag">{{ item.activityStatus || '已发布' }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post, imgUrl } from '../api'

const tab = ref('create')
const categories = ref([])
const activities = ref([])
const submitting = ref(false)
const formRef = ref(null)
const presetCovers = ['/imgs/campus/lecture-banner.svg', '/imgs/campus/sports-banner.svg', '/imgs/campus/arts-banner.svg', '/imgs/campus/club-banner.svg', '/imgs/campus/volunteer-banner.svg']
const form = ref(blank())
const rules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  typeId: [{ required: true, message: '请选择活动分类', trigger: 'change' }],
  address: [{ required: true, message: '请填写活动地址', trigger: 'blur' }],
  images: [{ required: true, message: '请填写活动图片地址', trigger: 'blur' }]
}

function blank() {
  return { name: '', typeId: null, description: '', images: '/imgs/campus/lecture-banner.svg', area: '', address: '', openHours: '', startTime: null, endTime: null, registrationDeadline: null, capacity: 50, avgPrice: 0 }
}
const cover = (a) => imgUrl((a.images || '').split(',')[0])

async function loadCategories() { categories.value = (await get('/activity-category/list')).data || [] }
async function loadActivities() { activities.value = (await get('/activity/mine')).data || [] }

function reset() { form.value = blank(); formRef.value && formRef.value.clearValidate() }

function submit() {
  formRef.value.validate(async (valid) => {
    if (!valid || submitting.value) return
    submitting.value = true
    try {
      await post('/activity', { ...form.value })
      ElMessage.success('活动发布成功')
      reset()
      tab.value = 'mine'
      loadActivities()
    } catch (e) { ElMessage.error(e.message) }
    finally { submitting.value = false }
  })
}

onMounted(() => { loadCategories(); loadActivities() })
</script>

<style scoped>
.full { grid-column: 1 / -1; }
.cover-opt { width: 96px; height: 64px; object-fit: cover; border-radius: 8px; cursor: pointer; border: 2px solid transparent; }
.cover-opt.active { border-color: var(--brand-1); }
</style>
