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
const assignDialog = ref(false)
const assignedTo = ref(0)
const form = reactive({ canteenId: 0, occurredAt: new Date(), category: '卫生问题', description: '', imagePath: '' })

async function load() {
  const [a, b] = await Promise.all([api.get('/feedback'), api.get('/canteens')])
  rows.value = a.data; canteens.value = b.data
}

async function save() {
  if (!form.canteenId || !form.description.trim()) return ElMessage.warning('请选择食堂并填写反馈内容')
  await api.post('/feedback', { ...form, occurredAt: toDateTime(form.occurredAt) })
  createDialog.value = false
  form.description = ''
  form.imagePath = ''
  ElMessage.success('反馈已提交至所选食堂总管')
  await load()
}

async function show(id: number) { detail.value = (await api.get(`/feedback/${id}`)).data }

watch(detail, async value => {
  if (value && auth.isManager && value.status === 'NEW') {
    assignedTo.value = auth.profile?.id ?? 0
    assignees.value = (await api.get(`/assignees/${value.canteen_id}`)).data
  }
})

async function assign() {
  if (!assignedTo.value || !detail.value) return ElMessage.warning('请选择处理人')
  detail.value = (await api.post(`/feedback/${detail.value.id}/assign`, { assignedTo: assignedTo.value })).data
  assignDialog.value = false
  await load()
}

async function investigate() {
  if (!detail.value) return
  const result = await ElMessageBox.prompt('请记录核查事实与处理措施', '食堂调查处理', { inputType: 'textarea', inputPattern: /\S+/, inputErrorMessage: '请填写处理情况' })
  detail.value = (await api.post(`/feedback/${detail.value.id}/investigate`, { note: result.value })).data
  ElMessage.success('调查处理已记录')
}

async function respond() {
  if (!detail.value) return
  const result = await ElMessageBox.prompt('输入给提交人的回复', '回复反馈', { inputType: 'textarea', inputPattern: /\S+/, inputErrorMessage: '请填写回复' })
  detail.value = (await api.post(`/feedback/${detail.value.id}/respond`, { response: result.value })).data
  await load()
}

async function act(action: 'close' | 'reopen') {
  if (!detail.value) return
  detail.value = (await api.post(`/feedback/${detail.value.id}/${action}`)).data
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>{{ auth.isCommunity ? '我的反馈' : auth.isManager ? '本食堂反馈' : auth.isInspector ? '反馈监管查看' : '分派给我的反馈' }}</h1><p>{{ auth.isCommunity ? '反馈直接送达所选食堂总管，监管人员可查看处理情况' : auth.isManager ? '受理、分派、调查并回复所属食堂的师生反馈' : auth.isInspector ? '查看各食堂反馈及处理结果；需要整改时可关联问题单' : '完成总管分派的调查任务' }}</p></div><el-button v-if="auth.isCommunity" type="primary" @click="createDialog=true">提交反馈</el-button></div>
  <div class="panel"><el-table :data="rows" stripe><el-table-column prop="id" label="编号" width="75" /><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="category" label="类别"><template #default="scope">{{ display(scope.row.category) }}</template></el-table-column><el-table-column prop="description" label="内容" min-width="220" show-overflow-tooltip /><el-table-column prop="occurred_at" label="发生时间" min-width="160"><template #default="scope">{{ display(scope.row.occurred_at) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'CLOSED' ? 'success' : scope.row.status === 'NEW' ? 'warning' : 'info'">{{ { NEW:'待受理', PROCESSING:'处理中', RESPONDED:'已回复', CLOSED:'已关闭' }[scope.row.status as 'NEW'] || scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default="scope"><el-button link type="primary" @click="show(scope.row.id)">详情</el-button></template></el-table-column></el-table></div>
  <el-dialog v-model="createDialog" title="提交食品安全反馈" width="550px"><el-alert title="提交后将直接进入所选食堂总管的待受理列表，学校监管人员也可查看。" type="info" :closable="false" style="margin-bottom:16px" /><el-form label-position="top"><el-form-item label="食堂"><el-select v-model="form.canteenId" style="width:100%"><el-option v-for="c in canteens" :key="String(c.id)" :label="String(c.name)" :value="Number(c.id)" /></el-select></el-form-item><el-form-item label="发生时间"><el-date-picker v-model="form.occurredAt" type="datetime" style="width:100%" /></el-form-item><el-form-item label="类别"><el-select v-model="form.category" style="width:100%"><el-option label="卫生问题" value="卫生问题" /><el-option label="食材质量" value="食材质量" /><el-option label="异物问题" value="异物问题" /><el-option label="其他" value="其他" /></el-select></el-form-item><el-form-item label="情况描述"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="5000" show-word-limit /></el-form-item><el-form-item label="现场图片（可选）"><FileUpload v-model="form.imagePath" purpose="FEEDBACK_IMAGE" :canteen-id="form.canteenId" image-only /></el-form-item></el-form><template #footer><el-button @click="createDialog=false">取消</el-button><el-button type="primary" @click="save">提交给食堂总管</el-button></template></el-dialog>
  <el-dialog :model-value="Boolean(detail)" @close="detail=null" title="反馈详情" width="630px"><template v-if="detail"><el-alert v-if="auth.isInspector" title="监管人员可查看全过程；需要正式整改时，请在问题整改中关联此反馈。" type="info" :closable="false" style="margin-bottom:16px" /><dl class="detail-list"><dt>编号</dt><dd>{{ detail.id }}</dd><dt>食堂</dt><dd>{{ canteens.find(x => Number(x.id) === Number(detail?.canteen_id))?.name }}</dd><dt>提交人</dt><dd>{{ detail.author_name }}</dd><dt>发生时间</dt><dd>{{ display(detail.occurred_at) }}</dd><dt>提交时间</dt><dd>{{ display(detail.created_at) }}</dd><dt>类别</dt><dd>{{ display(detail.category) }}</dd><dt>问题描述</dt><dd>{{ detail.description }}</dd><dt>现场图片</dt><dd><a v-if="detail.image_path" :href="String(detail.image_path)" target="_blank">查看图片</a><span v-else>未上传</span></dd><dt>当前状态</dt><dd>{{ display(detail.status) }}</dd><dt>受理处理人</dt><dd>{{ detail.assignee_name || '等待食堂总管受理' }}</dd><dt>调查记录</dt><dd>{{ detail.investigation_note || '尚未记录' }}<span v-if="detail.investigator_name">（{{ detail.investigator_name }}）</span></dd><dt>处理回复</dt><dd>{{ detail.response || '尚未回复' }}</dd><dt>关联问题单</dt><dd>{{ detail.issue_id || '无' }}</dd></dl><div class="actions" style="margin-top:25px"><el-button v-if="auth.isManager && detail.status === 'NEW'" type="primary" @click="assignDialog=true">受理并分派</el-button><el-button v-if="(auth.isManager || auth.isWorker) && detail.status === 'PROCESSING' && !detail.investigation_note" type="primary" @click="investigate">记录调查处理</el-button><el-button v-if="auth.isManager && detail.status === 'PROCESSING' && detail.investigation_note" type="primary" @click="respond">回复提交人</el-button><el-button v-if="auth.isManager && detail.status === 'RESPONDED'" @click="act('close')">结案</el-button><el-button v-if="auth.isCommunity && ['RESPONDED','CLOSED'].includes(String(detail.status))" @click="act('reopen')">申请继续处理</el-button></div></template></el-dialog>
  <el-dialog v-model="assignDialog" title="分派反馈" width="450px"><el-select v-model="assignedTo" placeholder="选择食堂处理人" style="width:100%"><el-option v-for="u in assignees" :key="String(u.id)" :label="String(u.display_name)" :value="Number(u.id)" /></el-select><template #footer><el-button @click="assignDialog=false">取消</el-button><el-button type="primary" @click="assign">确定分派</el-button></template></el-dialog>
</template>
