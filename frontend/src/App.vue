<template>
  <div>
    <header v-if="!isLoginPage" class="cp-nav">
      <div class="cp-nav-inner">
        <div class="cp-brand" @click="go('/')">
          <span class="cp-brand-icon">🎓</span>
          <span>校园派</span>
        </div>
        <nav class="cp-nav-links">
          <router-link class="cp-nav-link" :class="{ active: route.path === '/' }" to="/">发现</router-link>
          <router-link class="cp-nav-link" :class="{ active: route.path.startsWith('/activities') || route.path.startsWith('/activity') }" to="/activities">活动</router-link>
          <router-link class="cp-nav-link" :class="{ active: route.path === '/post/new' }" to="/post/new">发布动态</router-link>
          <router-link v-if="isStaff" class="cp-nav-link" :class="{ active: route.path === '/organizer' }" to="/organizer">工作台</router-link>
          <router-link v-if="isAdmin" class="cp-nav-link" :class="{ active: route.path === '/admin' }" to="/admin">管理后台</router-link>
        </nav>
        <div class="cp-nav-right">
          <template v-if="user">
            <div class="cp-user-chip" @click="go('/profile')">
              <img class="cp-avatar" :src="user.icon || '/imgs/icons/default-icon.png'" alt="" />
              <span>{{ user.nickName }}</span>
            </div>
            <el-dropdown trigger="click" @command="onUserCommand">
              <el-button text>更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="myPosts">我的动态</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" round @click="go('/login')">登录</el-button>
        </div>
      </div>
    </header>
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
      <AssistantDock v-if="!isLoginPage" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post as apiPost } from './api'
import AssistantDock from './components/AssistantDock.vue'

const route = useRoute()
const router = useRouter()

const user = computed(() => JSON.parse(localStorage.getItem('cp_user') || 'null'))
const isLoginPage = computed(() => route.path === '/login')
const isStaff = computed(() => user.value && ['ORGANIZER', 'ADMIN'].includes(user.value.role))
const isAdmin = computed(() => user.value && user.value.role === 'ADMIN')

function go(path) { router.push(path) }

function onUserCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'myPosts') router.push('/profile?tab=posts')
  else if (cmd === 'logout') logout()
}

async function logout() {
  try { await apiPost('/user/logout') } catch (e) { /* ignore */ }
  localStorage.removeItem('cp_token')
  localStorage.removeItem('cp_user')
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

