import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('./views/Home.vue') },
  { path: '/login', name: 'login', component: () => import('./views/Login.vue') },
  { path: '/activities', name: 'activities', component: () => import('./views/ActivityList.vue') },
  { path: '/activity/:id', name: 'activity-detail', component: () => import('./views/ActivityDetail.vue') },
  { path: '/profile', name: 'profile', component: () => import('./views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/profile/edit', name: 'profile-edit', component: () => import('./views/ProfileEdit.vue'), meta: { requiresAuth: true } },
  { path: '/user/:id', name: 'user-home', component: () => import('./views/UserHome.vue') },
  { path: '/post/:id', name: 'post-detail', component: () => import('./views/PostDetail.vue') },
  { path: '/post/new', name: 'post-edit', component: () => import('./views/PostEdit.vue'), meta: { requiresAuth: true } },
  { path: '/organizer', name: 'organizer', component: () => import('./views/Organizer.vue'), meta: { requiresAuth: true, roles: ['ORGANIZER', 'ADMIN'] } },
  { path: '/admin', name: 'admin', component: () => import('./views/Admin.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  const user = JSON.parse(localStorage.getItem('cp_user') || 'null')
  if (to.meta.requiresAuth && !user) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.roles && user && !to.meta.roles.includes(user.role)) return { path: '/' }
  return true
})

export default router
