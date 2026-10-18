<script setup lang="ts">
import { display } from '../display'
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, toDateTime } from '../api'
import FileUpload from '../components/FileUpload.vue'
import { useAuth } from '../store'

const auth = useAuth()
const rows = ref<Record<string, unknown>[]>([])
const canteens = ref<Record<string, unknown>[]>([])
const assignees = ref<Record<string, unknown>[]>([])
const detail = ref<Record<string, unknown> | null>(null)
const createDialog = ref(false)
const rectifyDialog = ref(false)
const reviewDialog = ref(false)
const eventDialog = ref(false)
const createForm = reactive({ canteenId: 0, sourceType: 'MANUAL', sourceId: null as number | null, category: '', severity: 'MEDIUM', description: '', location: '', evidencePath: '', assignedTo: 0, dueAt: new Date(Date.now() + 3 * 86400000), needsReport: false, reportNote: '' })
const rectifyForm = reactive({ details: '', evidencePath: '' })
const reviewForm = reactive({ result: 'APPROVED', note: '' })
const eventForm = reactive({ eventType: 'MEASURE', note: '' })

async function load() {
  const [a, b] = await Promise.all([api.get('/issues'), api.get('/canteens')])
  rows.value = a.data; canteens.value = b.data
}
watch(() => createForm.canteenId, async id => { assignees.value = id ? (await api.get(`/assignees/${id}`)).data : [] })

async function openDetail(id: number) { detail.value = (await api.get(`/issues/${id}`)).data }

async function saveIssue() {
  if (!createForm.canteenId || !createForm.assignedTo || !createForm.category || !createForm.description) return ElMessage.warning('请填写食堂、责任人、问题类别和描述')
  await api.post('/issues', { ...createForm, sourceId: createForm.sourceType === 'MANUAL' ? null : createForm.sourceId, dueAt: toDateTime(createForm.dueAt) })
  createDialog.value = false
  createForm.evidencePath = ''
  ElMessage.success('问题单已下发')
  await load()
}

async function submitRectification() {
  if (!detail.value || !rectifyForm.details.trim()) return ElMessage.warning('请填写整改说明')
  detail.value = (await api.post(`/issues/${detail.value.id}/rectifications`, rectifyForm)).data
  rectifyDialog.value = false
  rectifyForm.details = ''
  rectifyForm.evidencePath = ''
  await load()
}

async function review() {
  if (!detail.value || !reviewForm.note.trim()) return ElMessage.warning('请填写复核意见')
  detail.value = (await api.post(`/issues/${detail.value.id}/review`, reviewForm)).data
  reviewDialog.value = false
  await load()
}

async function addEvent() {
  if (!detail.value || !eventForm.note.trim()) return ElMessage.warning('请填写处置内容')
  detail.value = (await api.post(`/issues/${detail.value.id}/events`, eventForm)).data
  eventDialog.value = false
  eventForm.note = ''
  ElMessage.success('处置时间线已更新')
}

async function addOversight() {
  if (!detail.value) return
  const result = await ElMessageBox.prompt('填写校长督办意见', '发起督办',
    { inputType: 'textarea', inputPattern: /\S+/, inputErrorMessage: '督办意见不能为空' })
  detail.value = (await api.post(`/issues/${detail.value.id}/oversights`, { note: result.value })).data
  ElMessage.success('督办已发起')
}

async function closeOversight(id: number) {
  const result = await ElMessageBox.prompt('填写督办结束说明', '结束督办',
    { inputType: 'textarea', inputPattern: /\S+/, inputErrorMessage: '结束说明不能为空' })
  detail.value = (await api.post(`/oversights/${id}/close`, { note: result.value })).data
}

onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>问题整改</h1><p>问题下发、整改提交、监管复核全程留痕</p></div><el-button v-if="auth.isInspector" type="primary" @click="createDialog=true">下发问题单</el-button></div>
  <div class="panel"><el-table :data="rows" stripe><el-table-column prop="id" label="编号" width="70" /><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="category" label="类别"><template #default="scope">{{ display(scope.row.category) }}</template></el-table-column><el-table-column prop="severity" label="级别" width="85"><template #default="scope">{{ { LOW:'一般', MEDIUM:'中等', HIGH:'严重' }[scope.row.severity as 'LOW'] || display(scope.row.severity) }}</template></el-table-column><el-table-column prop="description" label="问题描述" min-width="220" show-overflow-tooltip /><el-table-column prop="assignee_name" label="责任人" /><el-table-column prop="due_at" label="期限" min-width="150"><template #default="scope">{{ display(scope.row.due_at) }}</template></el-table-column><el-table-column label="状态" width="125"><template #default="scope"><el-tag :type="scope.row.status === 'CLOSED' ? 'success' : scope.row.overdue ? 'danger' : 'warning'">{{ scope.row.status === 'CLOSED' ? '已关闭' : scope.row.status === 'REVIEW' ? '待复核' : scope.row.overdue ? '逾期整改' : '待整改' }}</el-tag></template></el-table-column><el-table-column label="操作" width="85"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button></template></el-table-column></el-table></div>

  <el-dialog v-model="createDialog" title="下发整改问题" width="650px"><el-form label-position="top" class="form-grid"><el-form-item label="食堂"><el-select v-model="createForm.canteenId" style="width:100%"><el-option v-for="c in canteens" :key="String(c.id)" :label="String(c.name)" :value="Number(c.id)" /></el-select></el-form-item><el-form-item label="整改责任人"><el-select v-model="createForm.assignedTo" style="width:100%"><el-option v-for="u in assignees" :key="String(u.id)" :label="String(u.display_name)" :value="Number(u.id)" /></el-select></el-form-item><el-form-item label="来源类型"><el-select v-model="createForm.sourceType" style="width:100%"><el-option label="现场手工登记" value="MANUAL" /><el-option label="检查任务" value="INSPECTION" /><el-option label="食材批次" value="BATCH" /><el-option label="师生反馈" value="FEEDBACK" /><el-option label="每日检查" value="DAILY_CHECK" /></el-select></el-form-item><el-form-item v-if="createForm.sourceType !== 'MANUAL'" label="来源记录编号"><el-input-number v-model="createForm.sourceId" :min="1" style="width:100%" /></el-form-item><el-form-item label="问题类别"><el-input v-model="createForm.category" placeholder="例如环境卫生、原料验收" /></el-form-item><el-form-item label="严重程度"><el-select v-model="createForm.severity" style="width:100%"><el-option label="一般" value="LOW" /><el-option label="中等" value="MEDIUM" /><el-option label="严重" value="HIGH" /></el-select></el-form-item><el-form-item label="整改期限"><el-date-picker v-model="createForm.dueAt" type="datetime" style="width:100%" /></el-form-item><el-form-item label="需人工上报"><el-switch v-model="createForm.needsReport" /></el-form-item><el-form-item label="发生位置"><el-input v-model="createForm.location" placeholder="例如二楼加工间" /></el-form-item><el-form-item class="wide" label="问题描述"><el-input v-model="createForm.description" type="textarea" :rows="3" /></el-form-item><el-form-item class="wide" label="现场照片或凭证"><FileUpload v-model="createForm.evidencePath" purpose="ISSUE_EVIDENCE" :canteen-id="createForm.canteenId" /></el-form-item><el-form-item v-if="createForm.needsReport" class="wide" label="上报说明"><el-input v-model="createForm.reportNote" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="createDialog=false">取消</el-button><el-button type="primary" @click="saveIssue">下发</el-button></template></el-dialog>

  <el-dialog :model-value="Boolean(detail)" @close="detail=null" title="问题处理记录" width="700px"><template v-if="detail"><dl class="detail-list"><dt>问题编号</dt><dd>{{ detail.id }}</dd><dt>描述</dt><dd>{{ detail.description }}</dd><dt>发生位置</dt><dd>{{ detail.location || '未填写' }}</dd><dt>现场凭证</dt><dd><a v-if="detail.evidence_path" :href="String(detail.evidence_path)" target="_blank">查看附件</a><span v-else>无</span></dd><dt>状态</dt><dd>{{ display(detail.status) }}</dd><dt>整改期限</dt><dd>{{ display(detail.due_at) }}</dd><dt>上报说明</dt><dd>{{ detail.report_note || '无' }}</dd></dl><template v-if="detail.needs_report"><h3>重大事项处置时间线</h3><el-timeline><el-timeline-item v-for="event in detail.events as Record<string, unknown>[]" :key="String(event.id)" :timestamp="display(event.created_at)"><strong>{{ display(event.event_type) }} · {{ event.actor_name }}</strong><p>{{ event.note }}</p></el-timeline-item></el-timeline></template><h3>校长督办</h3><el-timeline><el-timeline-item v-for="oversight in detail.oversights as Record<string, unknown>[]" :key="String(oversight.id)" :timestamp="display(oversight.created_at)"><strong>{{ oversight.principal_name }} · {{ oversight.status === 'OPEN' ? '进行中' : '已结束' }}</strong><p>{{ oversight.note }}</p><p v-if="oversight.close_note" class="muted">结束说明：{{ oversight.close_note }}</p><el-button v-if="auth.isPrincipal && oversight.status === 'OPEN'" link @click="closeOversight(Number(oversight.id))">结束督办</el-button></el-timeline-item></el-timeline><h3>整改与复核历史</h3><el-timeline><el-timeline-item v-for="r in detail.rectifications as Record<string, unknown>[]" :key="String(r.id)" :timestamp="display(r.submitted_at)"><strong>{{ r.submitter_name }}提交整改</strong><p>{{ r.details }}</p><p v-if="r.evidence_path"><a :href="String(r.evidence_path)" target="_blank">查看整改证据</a></p><p v-if="r.review_result" class="muted">复核：{{ display(r.review_result) }} · {{ r.review_note }}</p></el-timeline-item></el-timeline><div class="actions"><el-button v-if="auth.isPrincipal" @click="addOversight">发起督办</el-button><el-button v-if="detail.needs_report && (auth.isInspector || auth.isManager || auth.isWorker)" @click="eventDialog=true">记录处置进展</el-button><el-button v-if="(auth.isManager || auth.isWorker) && detail.status === 'OPEN'" type="primary" @click="rectifyDialog=true">提交整改</el-button><el-button v-if="auth.isInspector && detail.status === 'REVIEW'" type="success" @click="reviewDialog=true">复核整改</el-button></div></template></el-dialog>
  <el-dialog v-model="rectifyDialog" title="提交整改" width="520px"><el-form label-position="top"><el-form-item label="整改说明"><el-input v-model="rectifyForm.details" type="textarea" :rows="5" /></el-form-item><el-form-item label="整改证据"><FileUpload v-if="detail" v-model="rectifyForm.evidencePath" purpose="RECTIFICATION_EVIDENCE" :canteen-id="Number(detail.canteen_id)" /></el-form-item></el-form><template #footer><el-button @click="rectifyDialog=false">取消</el-button><el-button type="primary" @click="submitRectification">提交复核</el-button></template></el-dialog>
  <el-dialog v-model="eventDialog" title="记录重大事项处置" width="520px"><el-form label-position="top"><el-form-item label="动作类型"><el-select v-model="eventForm.eventType" style="width:100%"><el-option v-if="auth.isInspector" label="已人工上报" value="REPORTED" /><el-option label="处置措施" value="MEASURE" /><el-option label="后续跟进" value="FOLLOW_UP" /></el-select></el-form-item><el-form-item label="事实与措施"><el-input v-model="eventForm.note" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="eventDialog=false">取消</el-button><el-button type="primary" @click="addEvent">保存记录</el-button></template></el-dialog>
  <el-dialog v-model="reviewDialog" title="复核整改" width="520px"><el-form label-position="top"><el-form-item label="复核结果"><el-radio-group v-model="reviewForm.result"><el-radio value="APPROVED">通过并关闭</el-radio><el-radio value="REJECTED">退回继续整改</el-radio></el-radio-group></el-form-item><el-form-item label="复核意见"><el-input v-model="reviewForm.note" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="reviewDialog=false">取消</el-button><el-button type="primary" @click="review">保存复核</el-button></template></el-dialog>
</template>
