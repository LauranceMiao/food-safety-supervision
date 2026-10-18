<script setup lang="ts">
import { display, batchNumber } from '../display'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useAuth } from '../store'

const auth = useAuth()
const risks = ref<Record<string, unknown>[]>([])
const canteens = ref<Record<string, unknown>[]>([])
const inspectors = ref<Record<string, unknown>[]>([])
const suggestions = ref<Record<string, unknown>[]>([])
const unassigned = ref<Record<string, unknown>[]>([])
const trace = ref<Record<string, unknown> | null>(null)
const traceBatchId = ref<number | null>(null)
const selectedRisk = ref<Record<string, unknown> | null>(null)
const selectedStart = ref(nextMonday())
const generating = ref(false)
const adjustDialog = ref(false)
const adjustment = reactive({ id: 0, date: '', inspectorId: 0 })
const availabilityDialog = ref(false)
const availableForm = reactive({ inspectorId: 0, date: '', available: false, maxTasks: 2 })

function nextMonday() {
  const d = new Date()
  const shift = (8 - (d.getDay() || 7)) % 7 || 7
  d.setDate(d.getDate() + shift)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const breakdown = computed(() => {
  if (!selectedRisk.value) return null
  try { return JSON.parse(String(selectedRisk.value.breakdown_json)) as Record<string, unknown> }
  catch { return null }
})

async function load() {
  const [r, c] = await Promise.all([api.get('/risk'), api.get('/canteens')])
  risks.value = r.data; canteens.value = c.data
  if (auth.isInspector) inspectors.value = (await api.get('/inspectors')).data
  if (auth.isInspector || auth.isPrincipal || auth.isAdmin) await loadSchedule()
  availableForm.inspectorId ||= auth.profile?.id ?? 0
}

async function loadSchedule() { suggestions.value = (await api.get('/schedule', { params: { start: selectedStart.value } })).data }
async function generate() {
  generating.value = true
  try {
    const result = (await api.post('/schedule/generate', { start: selectedStart.value })).data
    ElMessage.info(result.message)
    unassigned.value = result.unassigned || []
    await loadSchedule()
  } finally { generating.value = false }
}

async function showTrace() {
  if (!traceBatchId.value) return ElMessage.warning('请输入批次编号')
  trace.value = (await api.get(`/trace/batches/${traceBatchId.value}`)).data
}

async function act(id: number, action: 'confirm' | 'dismiss') {
  await api.post(`/schedule/${id}/${action}`)
  ElMessage.success(action === 'confirm' ? '已生成正式检查任务' : '已忽略建议')
  await loadSchedule()
}

function openAdjust(row: Record<string, unknown>) {
  adjustment.id = Number(row.id); adjustment.date = String(row.plan_date); adjustment.inspectorId = Number(row.inspector_id)
  adjustDialog.value = true
}
async function saveAdjust() {
  await api.post(`/schedule/${adjustment.id}/adjust`, { date: adjustment.date, inspectorId: adjustment.inspectorId })
  adjustDialog.value = false
  await loadSchedule()
}
async function saveAvailability() {
  await api.put('/schedule/availability', availableForm)
  availabilityDialog.value = false
  ElMessage.success('可用时间已保存')
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>风险与排程</h1><p>可解释评分、食材批次追溯和风险驱动检查建议</p></div><el-button @click="load">刷新</el-button></div>
  <div class="panel"><div class="section-heading"><h2>A · 食堂风险评分</h2><span class="muted">内部排序指标，近90天记录</span></div><el-table :data="risks" stripe><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="score" label="风险分"><template #default="scope">{{ scope.row.score ?? '数据不足' }}</template></el-table-column><el-table-column label="等级"><template #default="scope"><el-tag :type="scope.row.level === 'HIGH' ? 'danger' : scope.row.level === 'MEDIUM' ? 'warning' : 'success'">{{ display(scope.row.level) }}</el-tag></template></el-table-column><el-table-column prop="config_version" label="参数版本" width="110" /><el-table-column prop="calculated_at" label="计算时间" min-width="160"><template #default="scope">{{ display(scope.row.calculated_at) }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="scope"><el-button link type="primary" @click="selectedRisk=scope.row">查看原因</el-button></template></el-table-column></el-table></div>
  <div class="panel"><div class="section-heading"><h2>B · 食材批次双向追溯</h2><span class="muted">只展示有记录的来源和去向</span></div><div class="inline-form"><el-input-number v-model="traceBatchId" :min="1" placeholder="批次编号" style="width:190px" /><el-button type="primary" @click="showTrace">查询批次</el-button></div><template v-if="trace"><el-alert v-for="w in trace.warnings as string[]" :key="w" :title="w" type="warning" :closable="false" style="margin-top:15px" /><p><strong>来源：</strong>{{ (trace.supplier as Record<string, unknown>)?.name }} → 批次 {{ batchNumber(trace.batch as Record<string, unknown>) }}</p><el-table :data="trace.affectedMenus as object[]"><el-table-column prop="dish_name" label="关联菜品" /><el-table-column prop="service_date" label="供餐日期"><template #default="scope">{{ display(scope.row.service_date) }}</template></el-table-column><el-table-column prop="meal" label="餐次"><template #default="scope">{{ display(scope.row.meal) }}</template></el-table-column><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="menu_status" label="状态"><template #default="scope">{{ display(scope.row.menu_status) }}</template></el-table-column></el-table></template></div>
  <div v-if="auth.isInspector || auth.isAdmin || auth.isPrincipal" class="panel"><div class="section-heading"><h2>C · 检查排程建议</h2><span class="muted">建议须由监管人员确认</span></div><div class="inline-form" style="margin-bottom:15px"><el-date-picker v-model="selectedStart" type="date" value-format="YYYY-MM-DD" placeholder="选择周一" /><el-button @click="loadSchedule">查看</el-button><el-button v-if="auth.isInspector" type="primary" :loading="generating" @click="generate">生成本周建议</el-button><el-button v-if="auth.isInspector" @click="availabilityDialog=true">我的可用时间</el-button></div><el-alert v-for="row in unassigned" :key="String(row.canteenId)" :title="`${canteens.find(c => Number(c.id) === Number(row.canteenId))?.name || row.canteenId} 未排入：${row.reason}`" type="warning" :closable="false" style="margin-bottom:10px" /><el-table :data="suggestions" stripe><el-table-column prop="plan_date" label="建议日期" width="120"><template #default="scope">{{ display(scope.row.plan_date) }}</template></el-table-column><el-table-column label="食堂"><template #default="scope">{{ canteens.find(x => Number(x.id) === Number(scope.row.canteen_id))?.name }}</template></el-table-column><el-table-column label="监管人员"><template #default="scope">{{ inspectors.find(x => Number(x.id) === Number(scope.row.inspector_id))?.display_name || scope.row.inspector_id }}</template></el-table-column><el-table-column prop="priority_score" label="优先值" width="95" /><el-table-column prop="reason" label="建议原因" min-width="270" /><el-table-column prop="status" label="状态" width="110"><template #default="scope">{{ display(scope.row.status) }}</template></el-table-column><el-table-column v-if="auth.isInspector" label="操作" width="190"><template #default="scope"><template v-if="scope.row.status === 'SUGGESTED'"><el-button link @click="openAdjust(scope.row)">调整</el-button><el-button link type="success" @click="act(scope.row.id,'confirm')">确认</el-button><el-button link type="danger" @click="act(scope.row.id,'dismiss')">忽略</el-button></template></template></el-table-column></el-table></div>
  <el-dialog :model-value="Boolean(selectedRisk)" @close="selectedRisk=null" title="评分依据" width="590px"><template v-if="selectedRisk"><p><strong>{{ selectedRisk.canteen_name }}</strong> · {{ selectedRisk.score ?? '数据不足' }} 分 · 参数版本 {{ selectedRisk.config_version }}</p><el-alert v-if="breakdown?.message" :title="String(breakdown.message)" type="warning" :closable="false" /><el-table v-if="breakdown?.contributions" :data="Object.entries(breakdown.contributions as Record<string, number>).map(([key, value]) => ({ key, value }))"><el-table-column label="指标"><template #default="scope">{{ scope.row.key === 'feedback' ? '未处理反馈' : display(scope.row.key) }}</template></el-table-column><el-table-column prop="value" label="贡献分" /></el-table><p class="muted">供应商证照、逾期整改、重复问题和未处理反馈均可影响评分。分值不代表事故概率。</p></template></el-dialog>
  <el-dialog v-model="adjustDialog" title="调整排程" width="470px"><el-form label-position="top"><el-form-item label="检查日期"><el-date-picker v-model="adjustment.date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="监管人员"><el-select v-model="adjustment.inspectorId" style="width:100%"><el-option v-for="i in inspectors" :key="String(i.id)" :label="String(i.display_name)" :value="Number(i.id)" /></el-select></el-form-item></el-form><template #footer><el-button @click="adjustDialog=false">取消</el-button><el-button type="primary" @click="saveAdjust">保存</el-button></template></el-dialog>
  <el-dialog v-model="availabilityDialog" title="设置可用时间" width="470px"><el-form label-position="top"><el-form-item label="日期"><el-date-picker v-model="availableForm.date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="当天可检查"><el-switch v-model="availableForm.available" /></el-form-item><el-form-item label="每日最多任务"><el-input-number v-model="availableForm.maxTasks" :min="1" :max="5" /></el-form-item></el-form><template #footer><el-button @click="availabilityDialog=false">取消</el-button><el-button type="primary" @click="saveAvailability">保存</el-button></template></el-dialog>
</template>
