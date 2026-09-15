<template>
  <div class="cp-container" style="max-width: 640px">
    <div class="cp-card">
      <h1 style="font-size:24px; margin:0 0 20px">编辑个人资料</h1>
      <el-form :model="form" label-position="top">
        <el-form-item label="昵称" required>
          <el-input v-model="form.nickName" maxlength="32" placeholder="给自己取个昵称" />
        </el-form-item>
        <el-form-item label="头像">
          <div class="avatar-picker">
            <img class="avatar-current" :src="form.icon || '/imgs/icons/default-icon.png'" alt="" />
            <div class="cp-gap">
              <img v-for="ic in presetIcons" :key="ic" class="avatar-option" :class="{ active: form.icon === ic }"
                   :src="ic" @click="form.icon = ic" />
            </div>
            <el-input v-model="form.icon" placeholder="或粘贴头像图片地址（/imgs/...）" style="margin-top:10px" />
          </div>
        </el-form-item>
        <el-form-item label="所在城市">
          <el-input v-model="form.city" maxlength="32" placeholder="例如：广州" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :label="0">保密</el-radio>
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="选择生日" style="width:100%" />
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="form.introduce" type="textarea" :rows="4" maxlength="128" show-word-limit placeholder="让大家更好地认识你" />
        </el-form-item>
        <div class="cp-gap">
          <el-button type="primary" round :loading="saving" @click="save">保存资料</el-button>
          <el-button round @click="$router.back()">返回</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, put } from '../api'

const router = useRouter()
const saving = ref(false)
const form = ref({ nickName: '', icon: '', city: '', introduce: '', gender: 0, birthday: null })
const presetIcons = [
  '/imgs/icons/default-icon.png',
  '/imgs/icons/icon1.jpg',
  '/imgs/icons/kkjtbcr.jpg',
  '/imgs/icons/user5-icon.png'
]

onMounted(async () => {
  try {
    const me = (await get('/user/me')).data
    form.value.nickName = me.nickName || ''
    form.value.icon = me.icon || ''
    const info = (await get('/user/info/' + me.id)).data
    if (info) {
      form.value.city = info.city || ''
      form.value.introduce = info.introduce || ''
      form.value.gender = info.gender ?? 0
      form.value.birthday = info.birthday || null
    }
  } catch (e) { ElMessage.error(e.message) }
})

async function save() {
  if (!form.value.nickName) return ElMessage.error('昵称不能为空')
  saving.value = true
  try {
    await put('/user/profile', {
      nickName: form.value.nickName, icon: form.value.icon,
      city: form.value.city, introduce: form.value.introduce,
      gender: form.value.gender, birthday: form.value.birthday || null
    })
    const me = (await get('/user/me')).data
    localStorage.setItem('cp_user', JSON.stringify(me))
    ElMessage.success('资料保存成功')
    router.push('/profile')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
</script>

<style scoped>
.avatar-picker { width: 100%; }
.avatar-current { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; margin-bottom: 10px; }
.avatar-option { width: 48px; height: 48px; border-radius: 50%; object-fit: cover; cursor: pointer; border: 2px solid transparent; }
.avatar-option.active { border-color: var(--brand-1); }
</style>
