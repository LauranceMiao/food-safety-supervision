<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useAuth } from '../store'
import { display } from '../display'
import { businessDate, todayInSchool } from '../businessDate'
import FileUpload from '../components/FileUpload.vue'

type Row = Record<string, any>
const auth = useAuth()
const canFill = computed(() => auth.isWorker)
const employee = computed(() => auth.isWorker && !auth.isManager && !auth.isAdmin && !auth.isInspector && !auth.isPrincipal)
const rows = ref<Row[]>([])
const canteens = ref<Row[]>([])
const submissions = ref<Row[]>([])
const reviews = ref<Row[]>([])
const loading = ref(false)
const loadError = ref('')
const selectedCanteen = ref(0)
const nameSearch = ref('')
const positionSearch = ref('')
const reviewTab = ref('PENDING')
const scoped = computed(() => canteens.value.filter(c => auth.profile?.canteenIds.includes(Number(c.id))))
const availableCanteens = computed(() => employee.value || auth.isManager ? scoped.value : canteens.value)
const genderNames: Record<string, string> = { UNSPECIFIED: '未填写', MALE: '男', FEMALE: '女' }
const statusNames: Record<string, string> = { DRAFT: '草稿', PENDING: '待总管审核', APPROVED: '已通过', REJECTED: '已退回' }

function matches(row: Row) {
  return (!selectedCanteen.value || Number(row.canteen_id) === selectedCanteen.value)
    && `${row.real_name || ''} ${row.display_name || ''}`.includes(nameSearch.value.trim())
    && String(row.position_name || '').includes(positionSearch.value.trim())
}
const visibleRows = computed(() => rows.value.filter(matches))
const history = computed(() => submissions.value.filter(matches).sort((a, b) => Number(b.id) - Number(a.id)))
const visibleReviews = computed(() => reviews.value.filter(matches)
  .filter(row => reviewTab.value === 'PENDING' ? row.status === 'PENDING' : ['APPROVED', 'REJECTED'].includes(row.status))
  .sort((a, b) => Number(b.id) - Number(a.id)))
const pendingCount = computed(() => reviews.value.filter(row => matches(row) && row.status === 'PENDING').length)
const latestRejections = computed(() => {
  const latest = new Map<number, Row>()
  for (const row of history.value) if (!latest.has(Number(row.canteen_id))) latest.set(Number(row.canteen_id), row)
  return [...latest.values()].filter(row => row.status === 'REJECTED')
})
const pendingSubmissions = computed(() => history.value.filter(row => row.status === 'PENDING'))
function errorMessage(error: any, fallback: string) { return error?.response?.data?.error || error?.message || fallback }
function statusType(status: string): 'info' | 'warning' | 'success' | 'danger' {
  return ({ DRAFT: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' } as Record<string, 'info' | 'warning' | 'success' | 'danger'>)[status] || 'info'
}
function reviewer(row: Row) { return row.reviewer_name || row.reviewed_by_name || '—' }
function rowName(row: Row) { return row.real_name || row.display_name || '未填写姓名' }
function isExpired(value: unknown) { const date = businessDate(value); return !!date && date < todayInSchool() }

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const requests = [api.get('/personnel'), api.get('/canteens')]
    const ownIndex = canFill.value ? requests.push(api.get('/personnel/my')) - 1 : -1
    const reviewIndex = auth.isManager ? requests.push(api.get('/personnel/reviews')) - 1 : -1
    const results = await Promise.all(requests)
    rows.value = results[0]!.data
    canteens.value = results[1]!.data
    if (ownIndex >= 0) submissions.value = results[ownIndex]!.data
    if (reviewIndex >= 0) reviews.value = results[reviewIndex]!.data
    if (employee.value && !selectedCanteen.value) selectedCanteen.value = Number(scoped.value[0]?.id || 0)
  } catch (error) { loadError.value = errorMessage(error, '健康档案加载失败，请重试') }
  finally { loading.value = false }
}

const editDialog = ref(false)
const editingId = ref<number | null>(null)
const editingVersion = ref(0)
const saving = ref(false)
const formError = ref('')
const sourceReviewNote = ref('')
const form = reactive({ canteenId: 0, realName: '', gender: 'UNSPECIFIED', contactPhone: '', positionName: '',
  healthCertNo: '', healthExpiresOn: '', certificatePath: '', lastTrainingOn: '', trainingNote: '' })
watch(() => form.canteenId, (id, previous) => {
  if (!editDialog.value || editingId.value || id === previous) return
  form.certificatePath = ''
  ElMessage.warning('已切换食堂，请为当前食堂重新上传本人健康证明')
}, { flush: 'sync' })
function defaultPosition() { return auth.isChef ? '厨师' : auth.isPurchaser ? '采购员' : auth.isCleaner ? '保洁员' : '' }
function openEditor(source?: Row) {
  if (!canFill.value || saving.value) return
  const canteenId = Number(source?.canteen_id || selectedCanteen.value || scoped.value[0]?.id || 0)
  if (!canteenId) { ElMessage.warning('尚未分配食堂，请联系超级管理员'); return }
  const pending = submissions.value.find(row => Number(row.canteen_id) === canteenId && row.status === 'PENDING')
  if (pending) { ElMessage.warning('该食堂的健康档案正在审核，请等待总管处理'); openDetail(pending); return }
  const draft = submissions.value.find(row => Number(row.canteen_id) === canteenId && row.status === 'DRAFT')
  const latest = [...submissions.value].filter(row => Number(row.canteen_id) === canteenId).sort((a, b) => Number(b.id) - Number(a.id))[0]
  const row = draft || source || latest || rows.value.find(row => Number(row.canteen_id) === canteenId)
  editingId.value = draft ? Number(draft.id) : null
  editingVersion.value = Number(draft?.version || 0)
  const rejected = source?.status === 'REJECTED' ? source : latest?.status === 'REJECTED' ? latest : null
  sourceReviewNote.value = rejected?.review_note || ''
  Object.assign(form, { canteenId, realName: row?.real_name || auth.profile?.displayName || '',
    gender: row?.gender || 'UNSPECIFIED', contactPhone: row?.contact_phone || '',
    positionName: row?.position_name || defaultPosition(), healthCertNo: row?.health_cert_no || '',
    healthExpiresOn: businessDate(row?.health_expires_on), certificatePath: row?.certificate_path || '',
    lastTrainingOn: businessDate(row?.last_training_on), trainingNote: row?.training_note || '' })
  formError.value = ''
  editDialog.value = true
}
function draftBody() {
  return { canteenId: form.canteenId, realName: form.realName.trim(), gender: form.gender,
    contactPhone: form.contactPhone.trim(), positionName: form.positionName.trim(),
    healthCertNo: form.healthCertNo.trim(), healthExpiresOn: form.healthExpiresOn || null,
    certificatePath: form.certificatePath, lastTrainingOn: form.lastTrainingOn || null, trainingNote: form.trainingNote.trim() }
}
function validateForm(submit: boolean) {
  if (!canFill.value || !scoped.value.some(row => Number(row.id) === form.canteenId)) return '请选择本人所属食堂'
  if (submissions.value.some(row => Number(row.canteen_id) === form.canteenId && row.status === 'PENDING')) return '该食堂已有待审核档案，请等待总管处理'
  if (submissions.value.some(row => Number(row.canteen_id) === form.canteenId && row.status === 'DRAFT' && Number(row.id) !== editingId.value)) return '该食堂已有草稿，请关闭弹窗后继续编辑该草稿'
  if (!submit) return ''
  if (!form.realName.trim()) return '请填写真实姓名'
  if (!form.positionName.trim()) return '请填写岗位'
  if (!form.healthCertNo.trim()) return '请填写健康证明编号'
  if (!form.healthExpiresOn) return '请选择健康证明到期日期'
  if (!form.certificatePath) return '请上传本人健康证明附件'
  return ''
}
async function persistDraft() {
  const response = editingId.value
    ? await api.put(`/personnel/my/submissions/${editingId.value}`, { ...draftBody(), version: editingVersion.value })
    : await api.post('/personnel/my/drafts', draftBody())
  const row = response.data as Row
  editingId.value = Number(row.id)
  editingVersion.value = Number(row.version)
  const index = submissions.value.findIndex(item => Number(item.id) === Number(row.id))
  if (index < 0) submissions.value.unshift(row)
  else submissions.value[index] = row
  return row
}
async function saveDraft() {
  if (saving.value || !editDialog.value) return
  formError.value = validateForm(false)
  if (formError.value) { ElMessage.warning(formError.value); return }
  saving.value = true
  try { await persistDraft(); ElMessage.success('草稿已保存，可继续填写后提交审核') }
  catch (error) { formError.value = errorMessage(error, '草稿保存失败，填写内容已保留') }
  finally { saving.value = false }
}
async function submitForReview() {
  if (saving.value || !editDialog.value) return
  formError.value = validateForm(true)
  if (formError.value) { ElMessage.warning(formError.value); return }
  saving.value = true
  try {
    const draft = await persistDraft()
    const response = await api.post(`/personnel/my/submissions/${draft.id}/submit`, { version: editingVersion.value })
    const index = submissions.value.findIndex(row => Number(row.id) === Number(draft.id))
    if (index >= 0) submissions.value[index] = response.data
    editDialog.value = false
    ElMessage.success('健康档案已提交，等待食堂总管审核')
    await load()
  } catch (error) { formError.value = errorMessage(error, '提交失败，填写内容已保留，请重试') }
  finally { saving.value = false }
}

const detailDialog = ref(false)
const detail = ref<Row | null>(null)
function openDetail(row: Row) { detail.value = { ...row }; detailDialog.value = true }
const reviewDialog = ref(false)
const reviewTarget = ref<Row | null>(null)
const reviewing = ref(false)
const reviewError = ref('')
const reviewForm = reactive({ decision: 'APPROVED', note: '' })
function openReview(row: Row) {
  if (!auth.isManager || row.status !== 'PENDING' || reviewing.value) return
  if (Number(row.user_id) === auth.profile?.id) { ElMessage.warning('本人提交的健康档案需由其他负责该食堂的总管审核'); return }
  reviewTarget.value = { ...row }
  reviewForm.decision = 'APPROVED'; reviewForm.note = ''; reviewError.value = ''
  reviewDialog.value = true
}
async function reviewSubmission() {
  if (reviewing.value || !reviewDialog.value || !reviewTarget.value || !auth.isManager) return
  if (reviewForm.decision === 'REJECTED' && !reviewForm.note.trim()) {
    reviewError.value = '退回时必须填写具体理由，工作人员将据此修改'
    ElMessage.warning(reviewError.value); return
  }
  reviewing.value = true; reviewError.value = ''
  try {
    await api.post(`/personnel/submissions/${reviewTarget.value.id}/review`, {
      decision: reviewForm.decision, note: reviewForm.note.trim(), version: Number(reviewTarget.value.version)
    })
    reviewDialog.value = false
    ElMessage.success(reviewForm.decision === 'APPROVED' ? '审核通过，人员健康页面已更新' : '已退回，工作人员可查看理由并重新提交')
    await load()
  } catch (error) { reviewError.value = errorMessage(error, '审核失败，请重试') }
  finally { reviewing.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-head">
    <div><h1>{{ employee ? '我的健康档案' : '人员健康' }}</h1><p>{{ employee ? '本人填写并提交健康信息，由食堂总管审核；审核通过后进入人员健康名册。' : auth.isManager ? '审核工作人员本人提交的资料；健康名册只展示已通过的档案。' : '查看各食堂工作人员审核通过的健康档案与培训信息。' }}</p></div>
    <el-button v-if="canFill" type="primary" :disabled="loading || !scoped.length" @click="openEditor()">填写我的健康档案</el-button>
  </div>
  <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="health-alert"><el-button link @click="load">重新加载</el-button></el-alert>
  <div class="panel health-filters">
    <el-select v-model="selectedCanteen" placeholder="筛选食堂" style="width:180px"><el-option v-if="!employee" label="全部食堂" :value="0" /><el-option v-for="c in availableCanteens" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select>
    <el-input v-model="nameSearch" clearable placeholder="按姓名查询" style="width:190px" />
    <el-input v-model="positionSearch" clearable placeholder="按岗位查询，如厨师" style="width:220px" />
  </div>
  <template v-if="canFill">
    <el-alert v-if="!scoped.length && !loading" title="尚未分配食堂，请联系超级管理员分配后填写健康档案。" type="warning" show-icon :closable="false" class="health-alert" />
    <el-alert v-for="row in latestRejections" :key="row.id" :title="`${row.canteen_name || '所属食堂'}的健康档案已退回`" :description="`退回理由：${row.review_note || '请联系总管确认修改要求'}`" type="error" show-icon :closable="false" class="health-alert"><el-button type="danger" link @click="openEditor(row)">按理由修改并重新提交</el-button></el-alert>
    <el-alert v-for="row in pendingSubmissions" :key="row.id" :title="`${row.canteen_name || '所属食堂'}的健康档案正在等待总管审核`" description="待审资料不能修改。已有通过档案时，会继续展示上一份通过的信息，待本次审核通过后更新。" type="info" show-icon :closable="false" class="health-alert" />
    <section class="panel"><h2>我的填写与审核记录</h2><p class="muted">草稿仅本人可见；被退回后可根据理由修改，再次提交将保留此前审核记录。</p>
      <el-table :data="history" stripe :loading="loading" empty-text="还没有填写记录，点击右上方“填写我的健康档案”开始填写">
        <el-table-column prop="canteen_name" label="食堂" min-width="110" /><el-table-column label="姓名" min-width="100"><template #default="scope">{{ rowName(scope.row) }}</template></el-table-column><el-table-column prop="position_name" label="岗位" min-width="100" />
        <el-table-column label="状态" min-width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusNames[scope.row.status] || scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column label="提交时间" min-width="155"><template #default="scope">{{ display(scope.row.submitted_at) }}</template></el-table-column>
        <el-table-column prop="review_note" label="审核意见 / 退回理由" min-width="210"><template #default="scope"><span :class="{ 'rejection-reason': scope.row.status === 'REJECTED' }">{{ scope.row.review_note || '—' }}</span></template></el-table-column>
        <el-table-column label="审核人" min-width="95"><template #default="scope">{{ reviewer(scope.row) }}</template></el-table-column>
        <el-table-column label="操作" width="170"><template #default="scope"><el-button link @click="openDetail(scope.row)">详情</el-button><el-button v-if="scope.row.status === 'DRAFT'" link type="primary" @click="openEditor(scope.row)">继续填写</el-button><el-button v-else-if="scope.row.status === 'REJECTED'" link type="danger" @click="openEditor(scope.row)">修改重提</el-button><el-button v-else-if="scope.row.status === 'APPROVED'" link type="primary" @click="openEditor(scope.row)">更新资料</el-button></template></el-table-column>
      </el-table>
    </section>
  </template>
  <section v-if="auth.isManager" class="panel">
    <h2>健康档案审核</h2>
    <el-tabs v-model="reviewTab"><el-tab-pane :label="`待审核（${pendingCount}）`" name="PENDING" /><el-tab-pane label="已审核记录" name="HISTORY" /></el-tabs>
    <el-table :data="visibleReviews" stripe :loading="loading" :empty-text="reviewTab === 'PENDING' ? '暂无待审核的健康档案' : '暂无已审核记录'">
      <el-table-column label="姓名" min-width="100"><template #default="scope">{{ rowName(scope.row) }}</template></el-table-column><el-table-column prop="display_name" label="账号姓名" min-width="100" /><el-table-column prop="canteen_name" label="食堂" min-width="110" /><el-table-column prop="position_name" label="岗位" min-width="100" />
      <el-table-column label="健康证到期" min-width="120"><template #default="scope"><span :class="{ 'rejection-reason': isExpired(scope.row.health_expires_on) }">{{ businessDate(scope.row.health_expires_on) || '未填写' }}</span></template></el-table-column>
      <el-table-column label="状态" min-width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusNames[scope.row.status] }}</el-tag></template></el-table-column>
      <el-table-column label="提交时间" min-width="155"><template #default="scope">{{ display(scope.row.submitted_at) }}</template></el-table-column><el-table-column prop="review_note" label="审核意见" min-width="160" />
      <el-table-column label="操作" width="150"><template #default="scope"><el-button v-if="scope.row.status === 'PENDING'" link type="primary" :disabled="Number(scope.row.user_id) === auth.profile?.id" :title="Number(scope.row.user_id) === auth.profile?.id ? '本人档案需由其他总管审核' : ''" @click="openReview(scope.row)">审核</el-button><el-button link @click="openDetail(scope.row)">详情</el-button></template></el-table-column>
    </el-table>
  </section>
  <section class="panel">
    <h2>{{ employee ? '我的已通过档案' : '已通过的人员健康名册' }}</h2>
    <p class="muted">{{ employee ? '首次提交需等待审核；更新资料审核通过前，原已通过档案保持展示。' : '姓名、岗位和健康证明以总管最近审核通过的资料为准。健康证明到期会以红色提示。' }}</p>
    <el-table :data="visibleRows" stripe :loading="loading" empty-text="暂无审核通过的健康档案">
      <el-table-column label="姓名" min-width="105"><template #default="scope">{{ rowName(scope.row) }}</template></el-table-column><el-table-column prop="canteen_name" label="食堂" min-width="110" /><el-table-column prop="position_name" label="岗位" min-width="100" /><el-table-column prop="contact_phone" label="联系电话" min-width="135" /><el-table-column prop="health_cert_no" label="健康证明编号" min-width="140" />
      <el-table-column label="证明到期" min-width="120"><template #default="scope"><el-tag v-if="isExpired(scope.row.health_expires_on)" type="danger">{{ businessDate(scope.row.health_expires_on) }} · 已到期</el-tag><span v-else>{{ businessDate(scope.row.health_expires_on) || '未填写' }}</span></template></el-table-column>
      <el-table-column label="最近培训" min-width="120"><template #default="scope">{{ businessDate(scope.row.last_training_on) || '未填写' }}</template></el-table-column><el-table-column label="审核人" min-width="90"><template #default="scope">{{ reviewer(scope.row) }}</template></el-table-column><el-table-column label="通过时间" min-width="155"><template #default="scope">{{ display(scope.row.approved_at) }}</template></el-table-column>
      <el-table-column label="操作" width="85"><template #default="scope"><el-button link @click="openDetail(scope.row)">详情</el-button></template></el-table-column>
    </el-table>
  </section>

  <el-dialog v-model="editDialog" :title="editingId ? '继续填写我的健康档案' : '填写我的健康档案'" width="720px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-alert v-if="sourceReviewNote" title="请根据退回理由修改后重新提交" :description="sourceReviewNote" type="error" show-icon :closable="false" class="health-alert" />
    <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="health-alert" />
    <p class="muted">只填写本人的健康资料。保存草稿可暂不填完整；提交审核前，请填齐标有“*”的信息并上传本人健康证明。</p>
    <el-form label-position="top" class="form-grid" :disabled="saving">
      <el-form-item label="所属食堂 *"><el-select v-model="form.canteenId" :disabled="!!editingId" style="width:100%"><el-option v-for="c in scoped" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select></el-form-item>
      <el-form-item label="当前账号"><el-input :model-value="`${auth.profile?.displayName || ''}（${auth.profile?.username || ''}）`" disabled /></el-form-item>
      <el-form-item label="真实姓名 *"><el-input v-model="form.realName" maxlength="80" placeholder="填写本人的真实姓名" /></el-form-item>
      <el-form-item label="性别"><el-select v-model="form.gender" style="width:100%"><el-option label="未填写" value="UNSPECIFIED" /><el-option label="男" value="MALE" /><el-option label="女" value="FEMALE" /></el-select></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="30" /></el-form-item>
      <el-form-item label="实际岗位 *"><el-input v-model="form.positionName" maxlength="80" placeholder="如厨师、采购员、保洁员" /><span class="muted">岗位说明不会改变账号权限</span></el-form-item>
      <el-form-item label="健康证明编号 *"><el-input v-model="form.healthCertNo" maxlength="100" /></el-form-item>
      <el-form-item label="健康证明到期日 *"><el-date-picker v-model="form.healthExpiresOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      <el-form-item class="wide" label="本人健康证明附件 *"><FileUpload v-model="form.certificatePath" purpose="STAFF_HEALTH_CERT" :canteen-id="form.canteenId" /><span class="muted">请上传本人健康证明的照片或 PDF。若原证明由总管代上传，请移除旧附件并由本人重新上传后再保存、提交。</span></el-form-item>
      <el-form-item label="最近培训日期"><el-date-picker v-model="form.lastTrainingOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      <el-form-item class="wide" label="培训记录"><el-input v-model="form.trainingNote" type="textarea" :rows="3" maxlength="1000" placeholder="可填写培训主题、培训地点等" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="editDialog=false">取消</el-button><el-button :disabled="saving" @click="saveDraft">保存草稿</el-button><el-button type="primary" :loading="saving" @click="submitForReview">{{ saving ? '正在保存…' : '提交总管审核' }}</el-button></template>
  </el-dialog>

  <el-dialog v-model="detailDialog" title="健康档案详情" width="720px">
    <template v-if="detail">
      <el-alert v-if="detail.status === 'REJECTED'" title="本次资料已退回" :description="`退回理由：${detail.review_note || '—'}`" type="error" show-icon :closable="false" class="health-alert" />
      <el-descriptions :column="2" border><el-descriptions-item label="姓名">{{ rowName(detail) }}</el-descriptions-item><el-descriptions-item label="账号姓名">{{ detail.display_name }}</el-descriptions-item><el-descriptions-item label="食堂">{{ detail.canteen_name }}</el-descriptions-item><el-descriptions-item label="岗位">{{ detail.position_name || '未填写' }}</el-descriptions-item><el-descriptions-item label="性别">{{ genderNames[detail.gender] || '未填写' }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ detail.contact_phone || '未填写' }}</el-descriptions-item><el-descriptions-item label="健康证编号">{{ detail.health_cert_no || '未填写' }}</el-descriptions-item><el-descriptions-item label="健康证到期">{{ businessDate(detail.health_expires_on) || '未填写' }}</el-descriptions-item><el-descriptions-item label="健康证明" :span="2"><a v-if="detail.certificate_path" :href="detail.certificate_path" target="_blank" rel="noopener noreferrer">查看本人健康证明附件</a><span v-else>未上传</span></el-descriptions-item><el-descriptions-item label="最近培训">{{ businessDate(detail.last_training_on) || '未填写' }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="statusType(detail.status || 'APPROVED')">{{ statusNames[detail.status || 'APPROVED'] }}</el-tag></el-descriptions-item><el-descriptions-item label="培训说明" :span="2"><span class="health-note">{{ detail.training_note || '未填写' }}</span></el-descriptions-item><el-descriptions-item label="审核人">{{ reviewer(detail) }}</el-descriptions-item><el-descriptions-item label="审核时间">{{ display(detail.reviewed_at || detail.approved_at) }}</el-descriptions-item><el-descriptions-item label="审核意见" :span="2"><span class="health-note">{{ detail.review_note || '—' }}</span></el-descriptions-item></el-descriptions>
    </template>
    <template #footer><el-button @click="detailDialog=false">关闭</el-button></template>
  </el-dialog>

  <el-dialog v-model="reviewDialog" title="审核工作人员健康档案" width="720px" :close-on-click-modal="false" :close-on-press-escape="!reviewing" :show-close="!reviewing">
    <template v-if="reviewTarget">
      <el-alert v-if="reviewError" :title="reviewError" type="error" show-icon :closable="false" class="health-alert" />
      <el-descriptions :column="2" border><el-descriptions-item label="真实姓名">{{ rowName(reviewTarget) }}</el-descriptions-item><el-descriptions-item label="账号姓名">{{ reviewTarget.display_name }}</el-descriptions-item><el-descriptions-item label="食堂">{{ reviewTarget.canteen_name }}</el-descriptions-item><el-descriptions-item label="实际岗位">{{ reviewTarget.position_name }}</el-descriptions-item><el-descriptions-item label="性别">{{ genderNames[reviewTarget.gender] || '未填写' }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ reviewTarget.contact_phone || '未填写' }}</el-descriptions-item><el-descriptions-item label="健康证编号">{{ reviewTarget.health_cert_no }}</el-descriptions-item><el-descriptions-item label="健康证到期"><span :class="{ 'rejection-reason': isExpired(reviewTarget.health_expires_on) }">{{ businessDate(reviewTarget.health_expires_on) }}{{ isExpired(reviewTarget.health_expires_on) ? '（已到期）' : '' }}</span></el-descriptions-item><el-descriptions-item label="健康证明" :span="2"><a v-if="reviewTarget.certificate_path" :href="reviewTarget.certificate_path" target="_blank" rel="noopener noreferrer">查看健康证明附件</a><span v-else>未上传</span></el-descriptions-item><el-descriptions-item label="最近培训" :span="2">{{ businessDate(reviewTarget.last_training_on) || '未填写' }}</el-descriptions-item><el-descriptions-item label="培训说明" :span="2"><span class="health-note">{{ reviewTarget.training_note || '未填写' }}</span></el-descriptions-item></el-descriptions>
      <el-form label-position="top" :disabled="reviewing" class="health-review-form"><el-form-item label="审核结果"><el-radio-group v-model="reviewForm.decision"><el-radio value="APPROVED">审核通过</el-radio><el-radio value="REJECTED">退回修改</el-radio></el-radio-group></el-form-item><el-form-item :label="reviewForm.decision === 'REJECTED' ? '退回理由 *' : '审核意见（选填）'"><el-input v-model="reviewForm.note" type="textarea" :rows="3" maxlength="500" :placeholder="reviewForm.decision === 'REJECTED' ? '请明确说明需要补充或修改的内容，工作人员会看到此理由' : '可填写核验说明'" /></el-form-item></el-form>
    </template>
    <template #footer><el-button :disabled="reviewing" @click="reviewDialog=false">取消</el-button><el-button :type="reviewForm.decision === 'REJECTED' ? 'danger' : 'primary'" :loading="reviewing" @click="reviewSubmission">{{ reviewing ? '正在提交…' : reviewForm.decision === 'REJECTED' ? '退回并告知理由' : '确认审核通过' }}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.health-filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.health-alert { margin-bottom: 16px; }
.rejection-reason { color: #c43f3f; font-weight: 500; white-space: pre-wrap; overflow-wrap: anywhere; }
.health-note { white-space: pre-wrap; overflow-wrap: anywhere; }
.health-review-form { margin-top: 22px; }
.panel h2 { margin: 0 0 14px; font-size: 20px; }
</style>
