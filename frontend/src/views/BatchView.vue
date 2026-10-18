<script setup lang="ts">
import { display, batchNumber } from '../display'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import FileUpload from '../components/FileUpload.vue'
import { useAuth } from '../store'
import { businessDate, todayInSchool } from '../businessDate'

const auth = useAuth()
const route = useRoute()
const batches = ref<Record<string, unknown>[]>([])
const suppliers = ref<Record<string, unknown>[]>([])
const plans = ref<Record<string, unknown>[]>([])
const detail = ref<Record<string, unknown> | null>(null)
const trace = ref<Record<string, unknown> | null>(null)
const createOpen = ref(false)
const acceptOpen = ref(false)
const selectedId = ref(0)
const form = reactive({ canteenId: 0, ingredientId: 0, specification: '', supplierId: 0, planId: null as number | null, productionDate: '', expiresOn: '', receivedOn: todayInSchool(), quantity: 1, voucherPath: '' })
const acceptance = reactive({ result: 'ACCEPTED', note: '', evidencePath: '' })
const canWrite = computed(() => auth.isPurchaser)
const canStop = computed(() => auth.isManager || auth.isInspector)
const availablePlans = computed(() => plans.value.filter(p => p.status === 'OPEN' && p.published_at
  && auth.profile?.canteenIds.includes(Number(p.canteen_id))))
const selectedPlan = computed(() => availablePlans.value.find(p => Number(p.id) === form.planId))
const supplierFixed = computed(() => Boolean(Number(selectedPlan.value?.supplier_id)))
const savingBatch = ref(false)
const savingAcceptance = ref(false)

async function load() {
  const [b, s, p] = await Promise.all([api.get('/catalog/batches'),
    auth.isPurchaser ? api.get('/catalog/suppliers') : Promise.resolve({ data: [] }),
    auth.isPurchaser ? api.get('/catalog/plans') : Promise.resolve({ data: [] })])
  batches.value = b.data; suppliers.value = s.data; plans.value = p.data
}

function selectPlan() {
  const plan = selectedPlan.value
  form.canteenId = Number(plan?.canteen_id || 0)
  form.ingredientId = Number(plan?.ingredient_id || 0)
  form.supplierId = Number(plan?.supplier_id || 0)
  form.quantity = plan ? Math.max(0.001, Number(plan.planned_quantity) - Number(plan.received_quantity || 0)) : 1
}

function newBatch(requestedPlanId?: number) {
  if (!auth.isPurchaser) return
  if (!availablePlans.value.length) return ElMessage.warning('当前没有可采购的已发布需求，请联系食堂总管发布需求')
  const matchingPlan = availablePlans.value.find(p => Number(p.id) === requestedPlanId)
  if (requestedPlanId && !matchingPlan) return ElMessage.warning('该需求未发布或已结束，请从列表选择有效采购需求')
  const plan = matchingPlan || availablePlans.value[0]
  Object.assign(form, { canteenId: 0, ingredientId: 0, specification: '', supplierId: 0, planId: Number(plan?.id), productionDate: '', expiresOn: '', receivedOn: todayInSchool(), quantity: 1, voucherPath: '' })
  selectPlan()
  createOpen.value = true
}

async function saveBatch() {
  if (savingBatch.value) return
  if (!selectedPlan.value || !form.canteenId || !form.ingredientId || !form.supplierId) return ElMessage.warning('请选择已发布的采购需求和启用的供应商')
  savingBatch.value = true
  try {
    await api.post('/catalog/batches', form)
    createOpen.value = false
    ElMessage.success('到货批次已登记；请在列表点击“验收”完成验收')
    await load()
  } finally { savingBatch.value = false }
}

async function openDetail(id: number) {
  detail.value = (await api.get(`/catalog/batches/${id}`)).data
}

function openAccept(id: number) { selectedId.value = id; Object.assign(acceptance, { result: 'ACCEPTED', note: '', evidencePath: '' }); acceptOpen.value = true }
async function saveAcceptance() {
  if (savingAcceptance.value) return
  savingAcceptance.value = true
  try {
    await api.post(`/catalog/batches/${selectedId.value}/acceptance`, acceptance)
    acceptOpen.value = false
    ElMessage.success(acceptance.result === 'ACCEPTED' ? '验收合格，厨师可领取此批次制作菜品' : '已拒收，该批次不会进入厨房可用库存')
    await load()
    await openDetail(selectedId.value)
  } finally { savingAcceptance.value = false }
}

async function stop(id: number) {
  const result = await ElMessageBox.prompt('请输入停用原因', '停用批次', { inputPattern: /\S+/, inputErrorMessage: '请填写原因' })
  await api.post(`/catalog/batches/${id}/stop`, { reason: result.value })
  await load()
  await openDetail(id)
}

async function showTrace(id: number) { trace.value = (await api.get(`/trace/batches/${id}`)).data }
onMounted(async () => {
  await load()
  if (auth.isPurchaser && route.query.planId) newBatch(Number(route.query.planId))
})
watch(() => route.query.planId, value => {
  if (value && auth.isPurchaser && plans.value.length) newBatch(Number(value))
})
</script>

<template>
  <div class="page-head"><div><h1>{{ auth.isChef ? '厨房合格库存' : auth.isPurchaser ? '采购与验收' : '到货与验收进度' }}</h1><p>{{ auth.isPurchaser ? '按总管已发布需求登记采购到货，核对食材、数量和凭证，再确认验收。' : auth.isChef ? '查看验收合格的食材批次，在厨房用料中登记实际领用；采购与验收由采购员负责。' : '查看采购员登记的到货与验收记录，跟进采购执行和食材安全。' }}</p></div><el-button v-if="canWrite" type="primary" @click="newBatch()">登记到货批次</el-button></div>
  <div class="panel"><el-table :data="batches" stripe empty-text="暂无食材批次"><el-table-column prop="id" label="批次编号" width="100"><template #default="scope">{{ batchNumber(scope.row) }}</template></el-table-column><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="ingredient_name" label="食材" /><el-table-column prop="supplier_name" label="供应商" min-width="130" /><el-table-column prop="received_on" label="到货日期" width="115"><template #default="scope">{{ businessDate(scope.row.received_on) }}</template></el-table-column><el-table-column label="库存" width="110"><template #default="scope">{{ scope.row.available_quantity }} {{ scope.row.ingredient_unit }}</template></el-table-column><el-table-column label="状态" width="105"><template #default="scope"><el-tag :type="scope.row.status === 'ACCEPTED' ? 'success' : scope.row.status === 'PENDING' ? 'warning' : 'danger'">{{ { PENDING:'待验收', ACCEPTED:'合格', REJECTED:'拒收', STOPPED:'停用' }[scope.row.status as 'PENDING'] || scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="210"><template #default="scope"><el-button link @click="openDetail(scope.row.id)">详情</el-button><el-button link type="primary" @click="showTrace(scope.row.id)">追溯</el-button><el-button v-if="canWrite && scope.row.status === 'PENDING'" link type="success" @click="openAccept(scope.row.id)">验收</el-button></template></el-table-column></el-table></div>

  <el-dialog v-model="createOpen" title="按采购需求登记到货" width="650px">
    <el-form label-position="top" class="form-grid">
      <el-alert title="先选择总管已发布的采购需求，所属食堂和食材自动填写；批次编号保存后自动生成。" type="info" :closable="false" class="wide" />
      <el-form-item class="wide" label="采购需求（必选）"><el-select v-model="form.planId" filterable style="width:100%" @change="selectPlan"><el-option v-for="p in availablePlans" :key="String(p.id)" :label="`计划 ${p.id} · ${p.canteen_name} · ${p.ingredient_name} · ${businessDate(p.planned_on)} · 需求 ${p.planned_quantity} ${p.ingredient_unit}`" :value="Number(p.id)" /></el-select></el-form-item>
      <el-form-item label="所属食堂"><el-input :model-value="String(selectedPlan?.canteen_name || '')" readonly /></el-form-item>
      <el-form-item label="需求食材"><el-input :model-value="String(selectedPlan?.ingredient_name || '')" readonly /></el-form-item>
      <el-form-item label="实际供应商"><el-select v-model="form.supplierId" filterable :disabled="supplierFixed" placeholder="选择启用的供应商" style="width:100%"><el-option v-for="s in suppliers.filter(row => Number(row.active) === 1 || Number(row.id) === Number(selectedPlan?.supplier_id))" :key="String(s.id)" :label="String(s.name)" :value="Number(s.id)" :disabled="Number(s.active) !== 1" /></el-select><span v-if="supplierFixed" class="muted">此供应商已由总管指定</span></el-form-item>
      <el-form-item label="规格型号"><el-input v-model="form.specification" placeholder="例如5kg/袋" /></el-form-item>
      <el-form-item :label="`实际到货数量（${selectedPlan?.ingredient_unit || '单位'}）`"><el-input-number v-model="form.quantity" :min="0.001" :precision="3" style="width:100%" /></el-form-item>
      <el-form-item label="到货日期"><el-date-picker v-model="form.receivedOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      <el-form-item label="生产日期"><el-date-picker v-model="form.productionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      <el-form-item label="保质期至"><el-date-picker v-model="form.expiresOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      <el-form-item class="wide" label="采购凭证"><FileUpload v-model="form.voucherPath" purpose="BATCH_VOUCHER" :canteen-id="form.canteenId" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="createOpen=false">取消</el-button><el-button type="primary" :loading="savingBatch" @click="saveBatch">保存到货批次</el-button></template>
  </el-dialog>
  <el-dialog v-model="acceptOpen" title="批次验收" width="500px"><el-form label-position="top"><el-form-item label="验收结果"><el-radio-group v-model="acceptance.result"><el-radio value="ACCEPTED">合格</el-radio><el-radio value="REJECTED">拒收</el-radio></el-radio-group></el-form-item><el-form-item label="验收说明"><el-input v-model="acceptance.note" type="textarea" :rows="3" /></el-form-item><el-form-item label="验收凭证"><FileUpload v-model="acceptance.evidencePath" purpose="ACCEPTANCE_EVIDENCE" :canteen-id="Number(batches.find(b => Number(b.id) === selectedId)?.canteen_id || 0)" /></el-form-item></el-form><template #footer><el-button @click="acceptOpen=false">取消</el-button><el-button type="primary" :loading="savingAcceptance" @click="saveAcceptance">提交验收</el-button></template></el-dialog>
  <el-dialog :model-value="Boolean(detail)" @close="detail=null" title="批次详情" width="700px"><template v-if="detail"><dl class="detail-list"><dt>批次编号</dt><dd>{{ batchNumber(detail) }}</dd><dt>食材</dt><dd>{{ detail.ingredient_name }}</dd><dt>规格型号</dt><dd>{{ detail.specification || '未填写' }}</dd><dt>供应商</dt><dd>{{ detail.supplier_name }}</dd><dt>关联采购计划</dt><dd>{{ detail.plan_id || '无' }}</dd><dt>数量 / 可用</dt><dd>{{ detail.quantity }} / {{ detail.available_quantity }}</dd><dt>到货日期</dt><dd>{{ businessDate(detail.received_on) }}</dd><dt>保质期至</dt><dd>{{ businessDate(detail.expires_on) || '未填写' }}</dd><dt>采购凭证</dt><dd><a v-if="detail.voucher_path" :href="String(detail.voucher_path)" target="_blank">查看附件</a><span v-else>无</span></dd><dt>状态</dt><dd>{{ { PENDING:'待验收', ACCEPTED:'合格', REJECTED:'拒收', STOPPED:'已停用' }[detail.status as 'PENDING'] || display(detail.status) }}</dd></dl><h3>验收记录</h3><el-table :data="detail.acceptances as object[]" size="small"><el-table-column prop="checked_at" label="时间"><template #default="scope">{{ display(scope.row.checked_at) }}</template></el-table-column><el-table-column prop="checker_name" label="验收人" /><el-table-column prop="result" label="结果"><template #default="scope">{{ scope.row.result === 'REJECTED' ? '拒收' : display(scope.row.result) }}</template></el-table-column><el-table-column prop="note" label="说明" /><el-table-column label="凭证"><template #default="scope"><a v-if="scope.row.evidence_path" :href="scope.row.evidence_path" target="_blank">查看</a></template></el-table-column></el-table><div style="margin-top:16px"><el-button v-if="canStop && detail.status === 'ACCEPTED'" type="danger" plain @click="stop(Number(detail.id))">停用批次</el-button></div></template></el-dialog>
  <el-dialog :model-value="Boolean(trace)" @close="trace=null" title="食材批次双向追溯" width="760px"><template v-if="trace"><el-alert v-for="w in trace.warnings as string[]" :key="w" :title="w" type="warning" :closable="false" style="margin-bottom:12px" /><p><strong>来源：</strong>{{ (trace.supplier as Record<string, unknown>)?.name }} → 批次 {{ batchNumber(trace.batch as Record<string, unknown>) }}</p><h3>关联供餐</h3><el-table :data="trace.affectedMenus as object[]" empty-text="暂无已登记的关联供餐"><el-table-column prop="service_date" label="供餐日期"><template #default="scope">{{ businessDate(scope.row.service_date) }}</template></el-table-column><el-table-column prop="meal" label="餐次"><template #default="scope">{{ display(scope.row.meal) }}</template></el-table-column><el-table-column prop="dish_name" label="菜品" /><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="quantity" label="使用量" /><el-table-column prop="menu_status" label="状态"><template #default="scope">{{ display(scope.row.menu_status) }}</template></el-table-column></el-table></template></el-dialog>
</template>
