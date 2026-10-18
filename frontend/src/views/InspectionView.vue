<script setup lang="ts">
import { display } from '../display'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, toDateTime } from '../api'
import { useAuth } from '../store'

type Row = Record<string, any>
const auth = useAuth()
const rows = ref<Row[]>([])
const canteens = ref<Row[]>([])
const inspectors = ref<Row[]>([])
const templates = ref<Row[]>([])
const detail = ref<Row | null>(null)
const detailDialog = ref(false)
const createDialog = ref(false)
const completeDialog = ref(false)
const completionTaskId = ref<number | null>(null)
const submitting = ref(false)
const submissionError = ref('')
const form = reactive({ canteenId: 0, inspectorId: 0, templateId: 0, scheduledAt: new Date(), note: '' })
const completeForm = reactive({ note: '', results: [] as { itemId: number; title: string; result: string; note: string }[] })

async function load() {
  const [a, b, t] = await Promise.all([api.get('/inspections'), api.get('/canteens'), api.get('/check-templates')])
  rows.value = a.data; canteens.value = b.data; templates.value = t.data
  if (auth.isInspector) inspectors.value = (await api.get('/inspectors')).data
  form.inspectorId ||= auth.profile?.id ?? 0
  form.templateId ||= Number(templates.value.find(t => t.active)?.id || 0)
}

async function save() {
  if (!form.canteenId || !form.inspectorId || !form.templateId) return ElMessage.warning('请选择食堂、检查人和模板')
  await api.post('/inspections', { ...form, scheduledAt: toDateTime(form.scheduledAt) })
  createDialog.value = false
  ElMessage.success('检查任务已创建')
  await load()
}

async function show(id: number) {
  detail.value = (await api.get(`/inspections/${id}`)).data
  detailDialog.value = true
}

async function start(row: Row) {
  await api.post(`/inspections/${row.id}/start`)
  ElMessage.success('检查已开始')
  await load()
  if (detail.value?.id === row.id) await show(row.id)
}

async function cancel(row: Row) {
  const result = await ElMessageBox.prompt('请输入取消原因', '取消检查任务', { inputType: 'textarea', inputPattern: /\S+/, inputErrorMessage: '必须填写原因' })
  await api.post(`/inspections/${row.id}/cancel`, { reason: result.value })
  await load()
  if (detail.value?.id === row.id) await show(row.id)
}

async function openComplete(row: Row) {
  const task = (await api.get(`/inspections/${Number(row.id)}`)).data as Row
  if (task.status !== 'IN_PROGRESS') {
    ElMessage.warning('该检查任务已不在进行中，请刷新后查看最新状态')
    await load()
    return
  }
  if (Number(task.inspector_id) !== auth.profile?.id) return ElMessage.warning('只能填写分派给自己的检查任务')
  const items = (await api.get(`/check-templates/${task.template_id}/items`)).data as Row[]
  const activeItems = items.filter(item => item.active)
  if (!activeItems.length) return ElMessage.warning('检查模板没有可用项目，请联系管理员维护模板')
  completionTaskId.value = Number(task.id)
  submissionError.value = ''
  completeForm.note = ''
  completeForm.results = activeItems.map(item => ({ itemId: Number(item.id), title: String(item.title), result: 'PASS', note: '' }))
  detailDialog.value = false
  completeDialog.value = true
}

async function complete() {
  if (submitting.value) return
  if (!completionTaskId.value || !completeDialog.value) return ElMessage.warning('请重新打开检查结果表单后提交')
  if (!completeForm.results.length) return ElMessage.warning('请先加载检查项目')
  if (completeForm.results.some(item => item.result === 'FAIL' && !item.note.trim())) {
    return ElMessage.warning('异常项目必须填写说明')
  }
  submitting.value = true
  submissionError.value = ''
  try {
    const payload = { note: completeForm.note, results: completeForm.results.map(item => ({ ...item })) }
    detail.value = (await api.post(`/inspections/${completionTaskId.value}/complete`, payload)).data
    completeDialog.value = false
    completionTaskId.value = null
    detailDialog.value = true
    ElMessage.success('检查已完成，结果已保存；异常项目请关联下发问题单')
  } catch (error) {
    const failure = error as { response?: { status?: number; data?: { error?: string } } }
    submissionError.value = failure.response?.data?.error
      || (failure.response?.status === 401 ? '登录已失效，请重新登录后重试' : '检查结果保存失败，请重试。填写内容已保留。')
    return
  } finally {
    submitting.value = false
  }
  await load().catch(() => ElMessage.warning('检查已保存，但列表刷新失败，请刷新页面查看'))
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>检查任务</h1><p>制定计划、逐项检查并记录结果</p></div><el-button v-if="auth.isInspector" type="primary" @click="createDialog=true">新建检查任务</el-button></div>
  <div class="panel"><el-table :data="rows" stripe><el-table-column prop="scheduled_at" label="计划时间" min-width="160"><template #default="scope">{{ display(scope.row.scheduled_at) }}</template></el-table-column><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="inspector_name" label="检查人" /><el-table-column label="状态" width="100"><template #default="scope">{{ { SCHEDULED:'待执行', IN_PROGRESS:'进行中', COMPLETED:'已完成', CANCELLED:'已取消' }[scope.row.status as 'SCHEDULED'] || scope.row.status }}</template></el-table-column><el-table-column prop="note" label="备注" min-width="160" show-overflow-tooltip /><el-table-column label="操作" width="240"><template #default="scope"><el-button link @click="show(scope.row.id)">详情</el-button><template v-if="auth.isInspector && Number(scope.row.inspector_id) === auth.profile?.id"><el-button v-if="scope.row.status === 'SCHEDULED'" link type="primary" @click="start(scope.row)">开始</el-button><el-button v-if="scope.row.status === 'IN_PROGRESS'" link type="success" @click="openComplete(scope.row)">填写结果</el-button><el-button v-if="scope.row.status === 'SCHEDULED'" link type="danger" @click="cancel(scope.row)">取消</el-button></template></template></el-table-column></el-table></div>
  <el-dialog v-model="createDialog" title="安排检查" width="520px"><el-form label-position="top"><el-form-item label="食堂"><el-select v-model="form.canteenId" style="width:100%"><el-option v-for="c in canteens.filter(c => c.active)" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select></el-form-item><el-form-item label="检查人员"><el-select v-model="form.inspectorId" style="width:100%"><el-option v-for="i in inspectors" :key="i.id" :label="i.display_name" :value="Number(i.id)" /></el-select></el-form-item><el-form-item label="检查模板"><el-select v-model="form.templateId" style="width:100%"><el-option v-for="t in templates.filter(t => t.active)" :key="t.id" :label="t.name" :value="Number(t.id)" /></el-select></el-form-item><el-form-item label="计划时间"><el-date-picker v-model="form.scheduledAt" type="datetime" style="width:100%" /></el-form-item><el-form-item label="备注"><el-input v-model="form.note" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="createDialog=false">取消</el-button><el-button type="primary" @click="save">创建任务</el-button></template></el-dialog>
  <el-dialog v-model="detailDialog" title="检查任务详情" width="620px"><template v-if="detail"><dl class="detail-list"><dt>任务编号</dt><dd>{{ detail.id }}</dd><dt>状态</dt><dd>{{ display(detail.status) }}</dd><dt>计划时间</dt><dd>{{ display(detail.scheduled_at) }}</dd><dt>开始时间</dt><dd>{{ detail.started_at ? display(detail.started_at) : '尚未开始' }}</dd><dt>完成时间</dt><dd>{{ detail.completed_at ? display(detail.completed_at) : '尚未完成' }}</dd><dt>检查结论</dt><dd>{{ detail.note || '无' }}</dd><dt>取消原因</dt><dd>{{ detail.cancel_reason || '无' }}</dd></dl><h3>逐项结果</h3><el-table :data="detail.results"><el-table-column prop="item_title" label="检查项" /><el-table-column prop="result" label="结果" width="100"><template #default="scope">{{ display(scope.row.result) }}</template></el-table-column><el-table-column prop="note" label="说明" /></el-table></template></el-dialog>
  <el-dialog v-model="completeDialog" title="填写现场检查结果" width="680px" :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting"><el-alert v-if="submissionError" :title="submissionError" type="error" :closable="false" style="margin-bottom:16px" /><el-alert title="异常项目需填写说明；如需整改，请在问题整改页面建立关联此任务的问题单。" type="info" :closable="false" style="margin-bottom:16px" /><div v-for="item in completeForm.results" :key="item.itemId" class="panel" style="margin-bottom:10px"><strong>{{ item.title }}</strong><div class="inline-form" style="margin-top:12px"><el-radio-group v-model="item.result"><el-radio value="PASS">正常</el-radio><el-radio value="FAIL">异常</el-radio><el-radio value="NA">不适用</el-radio></el-radio-group><el-input v-model="item.note" placeholder="说明，异常时必填" /></div></div><el-form-item label="检查结论"><el-input v-model="completeForm.note" type="textarea" :rows="3" /></el-form-item><template #footer><el-button :disabled="submitting" @click="completeDialog=false">取消</el-button><el-button type="primary" :loading="submitting" @click="complete">{{ submitting ? '正在保存…' : '完成检查' }}</el-button></template></el-dialog>
</template>
