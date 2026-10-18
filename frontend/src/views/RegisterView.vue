<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, prepareCsrf } from '../api'

const router = useRouter()
const busy = ref(false)
const form = reactive({ username: '', displayName: '', identityType: 'STUDENT', password: '', confirmPassword: '' })

async function submit() {
  if (!/^[A-Za-z0-9_]{4,32}$/.test(form.username)) return ElMessage.warning('用户名需为4至32位字母、数字或下划线')
  if (form.password.length < 8 || form.password.length > 64) return ElMessage.warning('密码需为8至64位')
  if (form.password !== form.confirmPassword) return ElMessage.warning('两次密码不一致')
  if (!form.displayName.trim()) return ElMessage.warning('请填写昵称')
  busy.value = true
  try {
    await prepareCsrf()
    await api.post('/auth/register', form)
    ElMessage.success('注册成功，请登录')
    await router.push('/login')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>师生注册</h1>
      <p>注册后可提交反馈并查看自己的处理进度</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" maxlength="32" autocomplete="username" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.displayName" maxlength="100" /></el-form-item>
        <el-form-item label="身份类别"><el-radio-group v-model="form.identityType"><el-radio value="STUDENT">学生</el-radio><el-radio value="TEACHER">教师</el-radio></el-radio-group></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-alert title="演示系统不验证学号或工号。请勿填写真实敏感信息。" type="info" :closable="false" style="margin-bottom:18px" />
        <el-button type="primary" size="large" :loading="busy" style="width:100%" native-type="submit">创建账号</el-button>
      </el-form>
      <div style="text-align:center;margin-top:20px;font-size:13px">已有账号？<router-link to="/login">返回登录</router-link></div>
    </div>
  </div>
</template>
