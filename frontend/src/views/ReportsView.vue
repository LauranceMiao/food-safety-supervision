<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { useAuth } from '../store'

type Row = Record<string, any>
const auth = useAuth()
const canteens = ref<Row[]>([])
const rows = ref<Row[]>([])
const canteenId = ref<number | null>(null)
const from = ref(dateString(new Date(Date.now() - 29 * 86400000)))
const to = ref(dateString(new Date()))
const definitions = ref<Record<string, string>>({})
const loading = ref(false)

function dateString(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const visibleCanteens = computed(() => canteens.value.filter(c =>
  auth.isAdmin || auth.isPrincipal || auth.isInspector || auth.profile?.canteenIds.includes(Number(c.id))))

function params() { return { from: from.value, to: to.value, ...(canteenId.value ? { canteenId: canteenId.value } : {}) } }

async function load() {
  loading.value = true
  try {
    const result = (await api.get('/reports/metrics', { params: params() })).data
    rows.value = result.canteens
    definitions.value = result.definitions
  } finally { loading.value = false }
}

async function exportCsv() {
  const response = await api.get('/reports/export', { params: params(), responseType: 'blob' })
  const url = URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `食堂食安统计_${from.value}_${to.value}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

onMounted(async () => {
  canteens.value = (await api.get('/canteens')).data
  await load()
})
</script>

<template>
  <div class="page-head"><div><h1>统计报表</h1><p>按日期和食堂汇总检查、整改、拒收与反馈情况</p></div><el-button type="primary" @click="exportCsv">导出 CSV</el-button></div>
  <div class="panel"><div class="inline-form"><el-date-picker v-model="from" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" /><span>至</span><el-date-picker v-model="to" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" /><el-select v-model="canteenId" clearable placeholder="全部授权食堂" style="width:190px"><el-option v-for="c in visibleCanteens" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select><el-button type="primary" :loading="loading" @click="load">查询</el-button></div></div>
  <div class="panel"><div class="section-heading"><h2>食堂统计</h2><span class="muted">{{ from }} 至 {{ to }}</span></div><el-table :data="rows" stripe><el-table-column prop="canteenName" label="食堂" min-width="110" /><el-table-column prop="inspectionCount" label="完成检查" width="95" /><el-table-column prop="issueCount" label="问题数" width="80" /><el-table-column prop="closedIssueCount" label="已关闭" width="80" /><el-table-column label="整改完成率" width="110"><template #default="scope">{{ scope.row.rectificationRate == null ? '无问题单' : `${scope.row.rectificationRate}%` }}</template></el-table-column><el-table-column prop="overdueIssueCount" label="逾期问题" width="95" /><el-table-column prop="rejectedBatchCount" label="拒收批次" width="95" /><el-table-column prop="feedbackCount" label="反馈数" width="80" /><el-table-column prop="handledFeedbackCount" label="已处理反馈" width="100" /><el-table-column label="反馈处理率" width="110"><template #default="scope">{{ scope.row.feedbackHandlingRate == null ? '无反馈' : `${scope.row.feedbackHandlingRate}%` }}</template></el-table-column></el-table><p class="muted">整改完成率：{{ definitions.rectificationRate }}。反馈处理率：{{ definitions.feedbackHandlingRate }}。</p></div>
</template>
