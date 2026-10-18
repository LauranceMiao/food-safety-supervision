<script setup lang="ts">
import { display } from './display'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from './store'
import { api } from './api'
import { ElMessage } from 'element-plus'
import { canOpenPath, navigationFor, primaryRole, roleLabel } from './roleAccess'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const passwordDialog = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
type Reminder = { key: string; title: string; target: string; dueAt?: string; read: boolean }
const reminders = ref<Reminder[]>([])
const unreadCount = computed(() => reminders.value.filter(item => !item.read).length)
let reminderTimer: ReturnType<typeof setInterval> | undefined
const publicPage = computed(() => ['/login', '/register'].includes(route.path))
const nav = computed(() => navigationFor(auth.profile))
const currentRole = computed(() => primaryRole(auth.profile))

async function logout() {
  await auth.logout()
  await router.push('/login')
}
async function changePassword() {
  if (passwordForm.newPassword.length < 8 || passwordForm.newPassword.length > 64) return ElMessage.warning('新密码需为8至64位')
  if (passwordForm.newPassword !== passwordForm.confirmPassword) return ElMessage.warning('两次输入的新密码不一致')
  await api.post('/auth/password', { oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
  passwordDialog.value = false
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  ElMessage.success('密码已修改')
}
async function loadReminders() {
  if (!auth.profile) { reminders.value = []; return }
  try { reminders.value = (await api.get<Reminder[]>('/reminders')).data.filter(item => canOpenPath(auth.profile, item.target)) }
  catch { reminders.value = [] }
}
async function openReminder(item: Reminder) {
  if (!item.read) await api.post('/reminders/read', { key: item.key })
  item.read = true
  await router.push(item.target)
}
watch(() => auth.profile?.id, loadReminders)
onMounted(() => { loadReminders(); reminderTimer = setInterval(loadReminders, 60000) })
onBeforeUnmount(() => { if (reminderTimer) clearInterval(reminderTimer) })
</script>

<template>
  <router-view v-if="publicPage" />
  <div v-else class="app-shell" :class="`role-${(currentRole || 'guest').toLowerCase()}`">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">食</span><div><strong>校园食安</strong><small>{{ roleLabel(auth.profile) }}工作台</small></div></div>
      <nav class="sidebar-nav">
        <div v-for="group in nav" :key="group.title" class="nav-group">
          <div class="nav-group-title">{{ group.title }}</div>
          <router-link v-for="item in group.items" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }">{{ item.label }}</router-link>
        </div>
      </nav>
      <div class="sidebar-foot">校内食堂 · 内部监管</div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="topbar-title">校园食品安全监管系统 <span>高校食堂</span></div>
        <div class="account">
          <span class="account-role">{{ roleLabel(auth.profile) }}</span><span>{{ auth.profile?.displayName || auth.profile?.username }}</span>
          <el-popover placement="bottom-end" :width="350" trigger="click"><template #reference><el-badge :value="unreadCount" :hidden="unreadCount === 0"><el-button text @click="loadReminders">提醒</el-button></el-badge></template><div v-if="!reminders.length" class="empty">暂无提醒</div><div v-for="item in reminders" :key="item.key" class="reminder-row" @click="openReminder(item)"><strong :style="{ opacity: item.read ? .55 : 1 }">{{ item.title }}</strong><small>{{ item.dueAt ? display(item.dueAt) : '待处理' }}</small></div></el-popover>
          <el-button text @click="passwordDialog=true">修改密码</el-button>
          <el-button text @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="main-content"><router-view /></main>
    </div>
    <el-dialog v-model="passwordDialog" title="修改密码" width="430px"><el-form label-position="top"><el-form-item label="原密码"><el-input v-model="passwordForm.oldPassword" type="password" show-password /></el-form-item><el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item><el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item></el-form><template #footer><el-button @click="passwordDialog=false">取消</el-button><el-button type="primary" @click="changePassword">保存</el-button></template></el-dialog>
  </div>
</template>
