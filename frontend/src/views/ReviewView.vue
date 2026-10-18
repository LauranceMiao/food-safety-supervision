<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { useAuth } from '../store'

type Row = Record<string, any>
const auth = useAuth()
const rows = ref<Row[]>([])
const canteens = ref<Row[]>([])
const dialog = ref(false)
const selected = ref<Row | null>(null)
const form = reactive({ periodType: 'WEEK', periodStart: previousMonday(), canteenId: null as number | null })
const canCreate = computed(() => auth.isManager || auth.isInspector || auth.isPrincipal || auth.isAdmin)
const scoped = computed(() => canteens.value.filter(c => auth.isPrincipal || auth.isAdmin || auth.isInspector
  || auth.profile?.canteenIds.includes(Number(c.id))))

function dayString(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function previousMonday() {
  const date = new Date()
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7) - 7)
  return dayString(date)
}
watch(() => form.periodType, type => {
  if (type === 'WEEK') form.periodStart = previousMonday()
  else { const date = new Date(); date.setDate(1); date.setMonth(date.getMonth() - 1); form.periodStart = dayString(date) }
})

async function load() {
  const [a, b] = await Promise.all([api.get('/reviews'), api.get('/canteens')])
  rows.value = a.data; canteens.value = b.data
  if (auth.isManager && !form.canteenId) form.canteenId = Number(auth.profile?.canteenIds[0] || 0)
}
function summary(row: Row) {
  try { return typeof row.summary_json === 'string' ? JSON.parse(row.summary_json) as Record<string, number> : row.summary_json as Record<string, number> }
  catch { return {} as Record<string, number> }
}
async function generate() {
  if (auth.isManager && !form.canteenId) return ElMessage.warning('请选择食堂')
  await api.post('/reviews/generate', form)
  dialog.value = false
  ElMessage.success('草稿已生成，请负责人补充结论并确认')
  await load()
}
async function confirm(row: Row) {
  const result = await ElMessageBox.prompt('补充本周期的人工研判、措施和下一步安排', '确认周期报告',
    { inputType: 'textarea', inputPattern: /\S+/, inputErrorMessage: '结论不能为空' })
  await api.post(`/reviews/${row.id}/confirm`, { conclusion: result.value })
  selected.value = null
  await load()
}
function canConfirm(row: Row) {
  return row.status === 'DRAFT' && (row.canteen_id == null
    ? auth.isPrincipal || auth.isAdmin
    : auth.isPrincipal || auth.isAdmin || auth.isManager && auth.profile?.canteenIds.includes(Number(row.canteen_id)))
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>周期排查与调度</h1><p>根据已记录的业务数据生成草稿，由负责人填写结论后确认</p></div><el-button v-if="canCreate" type="primary" @click="dialog=true">生成草稿</el-button></div>
  <div class="panel"><el-table :data="rows" stripe><el-table-column label="周期" width="80"><template #default="scope">{{ scope.row.period_type === 'WEEK' ? '周排查' : '月调度' }}</template></el-table-column><el-table-column prop="period_start" label="起始日期" width="120" /><el-table-column prop="period_end" label="结束日期" width="120" /><el-table-column label="范围" width="120"><template #default="scope">{{ scope.row.canteen_name || '全校' }}</template></el-table-column><el-table-column label="摘要" min-width="240"><template #default="scope">检查 {{ summary(scope.row).inspectionCount || 0 }} · 问题 {{ summary(scope.row).issueCount || 0 }} · 逾期 {{ summary(scope.row).overdueIssueCount || 0 }}</template></el-table-column><el-table-column prop="creator_name" label="创建人" width="105" /><el-table-column label="状态" width="90"><template #default="scope">{{ scope.row.status === 'CONFIRMED' ? '已确认' : '草稿' }}</template></el-table-column><el-table-column label="操作" width="130"><template #default="scope"><el-button link @click="selected=scope.row">详情</el-button><el-button v-if="canConfirm(scope.row)" link type="primary" @click="confirm(scope.row)">确认</el-button></template></el-table-column></el-table></div>
  <el-dialog v-model="dialog" title="生成周期草稿" width="540px"><el-form label-position="top"><el-form-item label="周期类型"><el-select v-model="form.periodType" style="width:100%"><el-option label="周排查" value="WEEK" /><el-option label="月调度" value="MONTH" /></el-select></el-form-item><el-form-item :label="form.periodType === 'WEEK' ? '该周周一' : '该月1日'"><el-date-picker v-model="form.periodStart" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="统计范围"><el-select v-model="form.canteenId" :clearable="!auth.isManager" placeholder="全校" style="width:100%"><el-option v-for="c in scoped" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="generate">生成草稿</el-button></template></el-dialog>
  <el-dialog :model-value="Boolean(selected)" @close="selected=null" title="周期报告详情" width="570px"><template v-if="selected"><dl class="detail-list"><dt>周期</dt><dd>{{ selected.period_start }} 至 {{ selected.period_end }}</dd><dt>范围</dt><dd>{{ selected.canteen_name || '全校' }}</dd><dt>完成检查</dt><dd>{{ summary(selected).inspectionCount }}</dd><dt>问题 / 未关闭</dt><dd>{{ summary(selected).issueCount }} / {{ summary(selected).unclosedIssueCount }}</dd><dt>逾期问题</dt><dd>{{ summary(selected).overdueIssueCount }}</dd><dt>拒收批次</dt><dd>{{ summary(selected).rejectedBatchCount }}</dd><dt>师生反馈</dt><dd>{{ summary(selected).feedbackCount }}</dd><dt>负责人结论</dt><dd>{{ selected.conclusion || '待填写' }}</dd><dt>确认人</dt><dd>{{ selected.confirmer_name || '尚未确认' }}</dd></dl><el-button v-if="canConfirm(selected)" type="primary" style="margin-top:18px" @click="confirm(selected)">填写结论并确认</el-button></template></el-dialog>
</template>
