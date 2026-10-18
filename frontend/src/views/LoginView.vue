<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuth } from '../store'

const router = useRouter()
const auth = useAuth()
const form = reactive({ username: '', password: '' })
const busy = ref(false)

async function submit() {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码')
  busy.value = true
  try {
    await auth.login(form.username, form.password)
    await router.replace(auth.isCommunity ? '/feedback' : '/')
  } catch {
    ElMessage.error('账号或密码不正确')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>欢迎使用校园食安</h1>
      <p>高校食堂食品安全监管系统</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" autocomplete="username" size="large" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" size="large" /></el-form-item>
        <el-button type="primary" size="large" :loading="busy" style="width:100%" native-type="submit">登录</el-button>
      </el-form>
      <div style="text-align:center;margin-top:20px;font-size:13px">师生还没有账号？<router-link to="/register">自行注册</router-link></div>
    </div>
  </div>
</template>
