<template>
  <div class="cp-container">
    <div class="cp-hero" style="margin-bottom:24px">
      <h1>系统管理工作台</h1>
      <p>查看平台用户、活动、报名异常与客服工单</p>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="用户与角色" name="users">
        <div class="cp-card">
          <div class="cp-row-between" style="margin-bottom:16px">
            <h2 class="cp-section-title" style="margin:0"><el-icon><User /></el-icon>用户与角色（{{ total }}）</h2>
            <div class="cp-gap">
              <el-input v-model="keyword" placeholder="搜索手机号或昵称" clearable style="width:220px" @keyup.enter="search" @clear="search" />
              <el-button type="primary" @click="search">查询</el-button>
            </div>
          </div>
          <el-table :data="users" stripe style="width:100%">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="nickName" label="昵称" min-width="150" />
            <el-table-column prop="phone" label="手机号" min-width="130" />
            <el-table-column label="角色" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="tagType(row.role)" round>{{ roleNames[row.role] || row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="170" />
          </el-table>
          <div style="display:flex; justify-content:flex-end; margin-top:16px">
            <el-pagination background layout="prev, pager, next" :page-size="20" :total="total" :current-page="current" @current-change="changePage" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="客服工单" name="support">
        <div class="cp-card">
          <div class="cp-row-between" style="margin-bottom:16px">
            <h2 class="cp-section-title" style="margin:0">客服工单（{{ supportTotal }}）</h2>
            <div class="cp-gap">
              <el-select v-model="supportStatus" clearable placeholder="全部状态" style="width:150px" @change="loadSupportTickets(1)">
                <el-option label="待处理" value="OPEN" />
                <el-option label="处理中" value="PROCESSING" />
                <el-option label="已解决" value="RESOLVED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
              <el-button type="primary" plain @click="loadSupportTickets(1)">刷新</el-button>
            </div>
          </div>
          <el-table :data="supportTickets" stripe style="width:100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="userId" label="用户" width="80" />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="subject" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="content" label="问题详情" min-width="240" show-overflow-tooltip />
            <el-table-column prop="createTime" label="提交时间" min-width="160" />
            <el-table-column prop="handlerId" label="处理人" width="90" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="supportTagType(row.status)" round>{{ supportStatusNames[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status === 'PROCESSING'" @click="updateTicket(row, 'PROCESSING')">受理</el-button>
                <el-button size="small" type="success" :disabled="row.status === 'RESOLVED'" @click="updateTicket(row, 'RESOLVED')">解决</el-button>
                <el-button size="small" type="info" :disabled="row.status === 'CLOSED'" @click="updateTicket(row, 'CLOSED')">关闭</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!supportTickets.length" class="cp-empty">暂无客服工单</div>
          <div v-else style="display:flex; justify-content:flex-end; margin-top:16px">
            <el-pagination background layout="prev, pager, next" :page-size="supportPageSize" :total="supportTotal" :current-page="supportCurrent" @current-change="loadSupportTickets" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="报名异常" name="deadLetters">
        <div class="cp-card">
          <div class="cp-row-between" style="margin-bottom:16px">
            <h2 class="cp-section-title" style="margin:0"><el-icon><Warning /></el-icon>报名死信（{{ dlTotal }}）</h2>
            <div class="cp-gap">
              <span class="cp-muted">自动重放仅处理可恢复错误，人工确认后再重放永久错误</span>
              <el-button type="primary" plain @click="loadDeadLetters(1)">刷新</el-button>
            </div>
          </div>
          <el-table :data="deadLetters" stripe style="width:100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="registrationId" label="报名流水" width="110" />
            <el-table-column prop="failureCode" label="失败码" width="180" />
            <el-table-column prop="failureReason" label="失败原因" min-width="220" show-overflow-tooltip />
            <el-table-column prop="reconsumeTimes" label="重试次数" width="90" />
            <el-table-column prop="retryCount" label="自动重试" width="90" />
            <el-table-column prop="createTime" label="产生时间" min-width="160" />
            <el-table-column prop="nextRetryTime" label="下次重试" min-width="160" />
            <el-table-column prop="lastRetryTime" label="最近重试" min-width="160" />
            <el-table-column prop="status" label="状态" width="140">
              <template #default="{ row }">
                <el-tag size="small" :type="deadLetterTagType(row.status)" round>{{ deadLetterStatusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain :disabled="deadLetterReplayDisabled(row.status)" :loading="row.replaying" @click="replay(row)">重放</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!deadLetters.length" class="cp-empty">暂无报名异常</div>
          <div v-else style="display:flex; justify-content:flex-end; margin-top:16px">
            <el-pagination background layout="prev, pager, next" :page-size="dlPageSize" :total="dlTotal" :current-page="dlCurrent" @current-change="loadDeadLetters" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="近期活动" name="activities">
        <div class="cp-card">
          <h2 class="cp-section-title"><el-icon><Collection /></el-icon>近期活动</h2>
          <div v-if="!activities.length" class="cp-empty">暂无活动数据</div>
          <div v-for="item in activities" :key="item.id" class="cp-post" @click="$router.push('/activity/' + item.id)">
            <img class="cp-post-img" :src="cover(item)" alt="" loading="lazy" />
            <div class="cp-post-body">
              <h3 class="cp-post-title">{{ item.name }}</h3>
              <div class="cp-post-content">组织者 ID：{{ item.organizerId || '-' }} · {{ item.area || '未填写区域' }} · {{ item.address }}</div>
              <div class="cp-post-foot"><span>已报名 {{ item.sold || 0 }} / {{ item.capacity || '不限' }}</span></div>
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
import { get, post, put, imgUrl } from '../api'

const roleNames = { STUDENT: '学生', ORGANIZER: '活动组织者', ADMIN: '管理员' }
const supportStatusNames = { OPEN: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }
const tab = ref('users')
const users = ref([])
const activities = ref([])
const keyword = ref('')
const current = ref(1)
const total = ref(0)

const deadLetters = ref([])
const dlCurrent = ref(1)
const dlPageSize = ref(20)
const dlTotal = ref(0)

const supportTickets = ref([])
const supportStatus = ref('')
const supportCurrent = ref(1)
const supportPageSize = ref(20)
const supportTotal = ref(0)

const cover = (a) => imgUrl((a.images || '').split(',')[0])
const tagType = (r) => r === 'ADMIN' ? 'danger' : r === 'ORGANIZER' ? 'warning' : 'success'
const supportTagType = (s) => s === 'OPEN' ? 'warning' : s === 'PROCESSING' ? 'primary' : s === 'RESOLVED' ? 'success' : 'info'
const deadLetterTagType = (s) => {
  if (s === 'REPLAYED' || s === 'AUTO_REPLAYED') return 'success'
  if (s === 'AUTO_RETRYING') return 'warning'
  if (s === 'PENDING') return 'info'
  return 'danger'
}
const deadLetterStatusName = (s) => ({
  PENDING: '待处理',
  AUTO_RETRYING: '自动重试中',
  AUTO_REPLAYED: '自动重放完成',
  MANUAL_REQUIRED: '待人工处理',
  REPLAYED: '人工重放完成',
}[s] || s)
const deadLetterReplayDisabled = (s) => ['REPLAYED', 'AUTO_REPLAYED', 'AUTO_RETRYING'].includes(s)

async function loadUsers() {
  try {
    const res = await get('/user/admin/users', { current: current.value, keyword: keyword.value })
    const data = res.data || {}
    users.value = data.records || []
    total.value = Number(data.total) || 0
    current.value = Number(data.current) || 1
  } catch (e) { ElMessage.error(e.message) }
}

async function loadActivities() {
  try { activities.value = (await get('/activity/search', { current: 1 })).data || [] } catch (e) { ElMessage.error(e.message) }
}

async function loadDeadLetters(page = 1) {
  dlCurrent.value = page
  try {
    const res = await get('/admin/registration/dead-letters', { current: page, pageSize: dlPageSize.value })
    const data = res.data || {}
    deadLetters.value = (data.records || []).map(x => ({ ...x, replaying: false }))
    dlTotal.value = Number(data.total) || 0
    dlCurrent.value = Number(data.current) || 1
  } catch (e) { ElMessage.error(e.message) }
}

async function loadSupportTickets(page = 1) {
  supportCurrent.value = page
  try {
    const res = await get('/admin/support-tickets', {
      current: page,
      pageSize: supportPageSize.value,
      status: supportStatus.value || undefined,
    })
    const data = res.data || {}
    supportTickets.value = data.records || []
    supportTotal.value = Number(data.total) || 0
    supportCurrent.value = Number(data.current) || 1
  } catch (e) { ElMessage.error(e.message) }
}

async function updateTicket(row, status) {
  try {
    await put('/admin/support-tickets/' + row.id + '/status', { status })
    ElMessage.success('工单状态已更新')
    await loadSupportTickets(supportCurrent.value)
  } catch (e) { ElMessage.error(e.message) }
}

async function replay(row) {
  row.replaying = true
  try {
    await post('/admin/registration/dead-letters/' + row.id + '/replay')
    ElMessage.success('已重新投递，请稍后关注处理结果')
    await loadDeadLetters(dlCurrent.value)
  } catch (e) { ElMessage.error(e.message) }
  finally { row.replaying = false }
}

function search() { current.value = 1; loadUsers() }
function changePage(p) { current.value = p; loadUsers() }

onMounted(() => { loadUsers(); loadActivities(); loadDeadLetters(); loadSupportTickets() })
</script>
