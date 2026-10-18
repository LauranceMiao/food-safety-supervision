<script setup lang="ts">
import { display } from '../display'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { useAuth } from '../store'
import { useRouter } from 'vue-router'
import { businessDate, todayInSchool } from '../businessDate'

type Row = Record<string, any>
type IngredientSuggestion = { value: string; id: number }
const auth = useAuth()
const router = useRouter()
const rows = ref<Row[]>([])
const canteens = ref<Row[]>([])
const ingredients = ref<Row[]>([])
const suppliers = ref<Row[]>([])
const dialog = ref(false)
const ingredientDialog = ref(false)
const continuePlanAfterIngredient = ref(false)
const ingredientName = ref('')
const savingPlan = ref(false)
const savingIngredient = ref(false)
const newIngredient = reactive({ name: '', category: '', unit: 'kg' })
const form = reactive({ canteenId: 0, ingredientId: 0, supplierId: null as number | null, plannedOn: todayInSchool(), plannedQuantity: 1 })
const scoped = computed(() => canteens.value.filter(c => auth.profile?.canteenIds.includes(Number(c.id))))

async function load() {
  const [p, c, i, s] = await Promise.all([api.get('/catalog/plans'), api.get('/canteens'),
    api.get('/catalog/ingredients'), api.get('/catalog/suppliers')])
  rows.value = p.data; canteens.value = c.data; ingredients.value = i.data; suppliers.value = s.data
  form.canteenId ||= Number(scoped.value[0]?.id || 0)
}

function openPlan() {
  ingredientName.value = ''
  form.ingredientId = 0
  form.supplierId = null
  form.plannedOn = todayInSchool()
  form.plannedQuantity = 1
  dialog.value = true
}

function searchIngredients(query: string, callback: (results: IngredientSuggestion[]) => void) {
  const keyword = query.trim().toLocaleLowerCase()
  callback(ingredients.value
    .filter(item => String(item.name).toLocaleLowerCase().includes(keyword))
    .map(item => ({ value: String(item.name), id: Number(item.id) })))
}

function selectIngredient(item: IngredientSuggestion) {
  ingredientName.value = item.value
  form.ingredientId = item.id
}

function onIngredientInput() {
  form.ingredientId = 0
}

function openIngredientDialog(continuePlan = false) {
  continuePlanAfterIngredient.value = continuePlan
  Object.assign(newIngredient, { name: ingredientName.value.trim(), category: '', unit: 'kg' })
  ingredientDialog.value = true
}

async function savePlan() {
  if (savingPlan.value) return
  if (!form.canteenId || !form.plannedOn) return ElMessage.warning('请选择食堂和计划日期')
  savingPlan.value = true
  try {
    await api.post('/catalog/plans', form)
    dialog.value = false
    ElMessage.success('采购需求已保存为草稿；发布后采购员才能看到并登记采购验收')
    await load()
  } finally {
    savingPlan.value = false
  }
}

async function save() {
  const name = ingredientName.value.trim()
  if (!name) return ElMessage.warning('请输入食材名称')
  if (!form.canteenId || !form.plannedOn) return ElMessage.warning('请选择食堂和计划日期')
  const existing = ingredients.value.find(item => String(item.name).trim().toLocaleLowerCase() === name.toLocaleLowerCase())
  if (existing) {
    selectIngredient({ value: String(existing.name), id: Number(existing.id) })
  } else {
    openIngredientDialog(true)
    return
  }
  await savePlan()
}

async function saveIngredient() {
  if (savingIngredient.value) return
  const name = newIngredient.name.trim()
  const unit = newIngredient.unit.trim()
  if (!name || !unit) return ElMessage.warning('请填写食材名称和计量单位')
  const existing = ingredients.value.find(item => String(item.name).trim().toLocaleLowerCase() === name.toLocaleLowerCase())
  savingIngredient.value = true
  try {
    const item = existing || (await api.post('/catalog/ingredients', {
      name, category: newIngredient.category.trim(), unit
    })).data
    if (!existing) ingredients.value.push(item)
    selectIngredient({ value: String(item.name), id: Number(item.id) })
    ingredientDialog.value = false
    ElMessage.success(existing ? '已选中已有食材' : '食材已加入目录并选中')
    if (continuePlanAfterIngredient.value) await savePlan()
  } finally {
    savingIngredient.value = false
  }
}

async function changeStatus(row: Row, status: 'CLOSED' | 'CANCELLED') {
  await ElMessageBox.confirm(`确定${status === 'CLOSED' ? '结束' : '撤销'}该采购计划？历史批次仍保留。`, '确认操作')
  await api.post(`/catalog/plans/${row.id}/status`, { status })
  await load()
}

async function publishPlan(row: Row) {
  await ElMessageBox.confirm(`发布“${row.ingredient_name}”的采购需求？采购员将按此需求登记采购与验收。`, '发布采购需求')
  await api.post(`/catalog/plans/${row.id}/publish`)
  ElMessage.success('采购需求已发布给本食堂采购员')
  await load()
}

function registerPurchase(row: Row) {
  router.push({ path: '/batches', query: { planId: String(row.id) } })
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>{{ auth.isPurchaser ? '待采购需求' : '采购需求计划' }}</h1><p>{{ auth.isManager ? '总管填写所需食材、日期和数量，发布后交由采购员采购与验收；供应商可指定，也可由采购员选择。' : '按总管已发布的需求采购，登记实际到货批次并完成验收；需求数量由总管制定。' }}</p></div><el-button v-if="auth.isManager" type="primary" @click="openPlan">新增采购需求</el-button></div>
  <div class="panel">
    <el-table :data="rows" stripe empty-text="暂无采购需求">
      <el-table-column prop="id" label="计划编号" width="90" />
      <el-table-column label="需求日期" width="120"><template #default="scope">{{ businessDate(scope.row.planned_on) }}</template></el-table-column>
      <el-table-column prop="canteen_name" label="食堂" />
      <el-table-column prop="ingredient_name" label="食材" />
      <el-table-column label="供应商" min-width="120"><template #default="scope">{{ scope.row.supplier_name || '由采购员选择' }}</template></el-table-column>
      <el-table-column label="需求量" width="110"><template #default="scope">{{ scope.row.planned_quantity }} {{ scope.row.ingredient_unit }}</template></el-table-column>
      <el-table-column label="到货量" width="110"><template #default="scope">{{ scope.row.received_quantity }} {{ scope.row.ingredient_unit }}</template></el-table-column>
      <el-table-column label="合格量" width="110"><template #default="scope">{{ scope.row.accepted_quantity }} {{ scope.row.ingredient_unit }}</template></el-table-column>
      <el-table-column label="状态" width="95"><template #default="scope"><el-tag :type="scope.row.status !== 'OPEN' ? 'info' : scope.row.published_at ? 'success' : 'warning'">{{ scope.row.status !== 'OPEN' ? display(scope.row.status) : scope.row.published_at ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
      <el-table-column v-if="auth.isManager || auth.isPurchaser" label="操作" min-width="185"><template #default="scope">
        <template v-if="scope.row.status === 'OPEN' && auth.isManager"><el-button v-if="!scope.row.published_at" link type="primary" @click="publishPlan(scope.row)">发布需求</el-button><el-button v-if="scope.row.published_at" link @click="changeStatus(scope.row,'CLOSED')">结束</el-button><el-button link type="danger" @click="changeStatus(scope.row,'CANCELLED')">撤销</el-button></template>
        <el-button v-if="auth.isPurchaser && scope.row.status === 'OPEN' && scope.row.published_at" link type="primary" @click="registerPurchase(scope.row)">采购登记到货</el-button>
      </template></el-table-column>
    </el-table>
  </div>
  <el-dialog v-model="dialog" title="新增采购需求草稿" width="560px">
    <el-form label-position="top" class="form-grid">
      <el-form-item label="食堂"><el-select v-model="form.canteenId" style="width:100%"><el-option v-for="c in scoped" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select></el-form-item>
      <el-form-item label="计划日期"><el-date-picker v-model="form.plannedOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      <el-form-item label="食材">
        <div style="display:flex;gap:8px;width:100%">
          <el-autocomplete v-model="ingredientName" :fetch-suggestions="searchIngredients" placeholder="输入食材名称，如牛肉" style="flex:1;min-width:0" @input="onIngredientInput" @select="selectIngredient" />
          <el-button @click="openIngredientDialog()">新增食材</el-button>
        </div>
        <div class="muted">可选择已有食材；输入新名称后保存计划，会提示补充类别和单位。</div>
      </el-form-item>
      <el-form-item label="指定供应商（可选）"><el-select v-model="form.supplierId" filterable clearable placeholder="由采购员选择" style="width:100%"><el-option v-for="s in suppliers.filter(item => Number(item.active) === 1)" :key="s.id" :label="s.name" :value="Number(s.id)" /></el-select></el-form-item>
      <el-form-item label="计划数量"><el-input-number v-model="form.plannedQuantity" :min="0.001" :precision="3" style="width:100%" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="savingPlan" @click="save">保存草稿</el-button></template>
  </el-dialog>
  <el-dialog v-model="ingredientDialog" title="新增食材" width="500px" append-to-body>
    <p v-if="continuePlanAfterIngredient" class="muted">食材目录中没有“{{ newIngredient.name }}”。补充信息后会继续创建采购计划。</p>
    <el-form label-position="top">
      <el-form-item label="名称"><el-input v-model="newIngredient.name" placeholder="例如牛肉" /></el-form-item>
      <el-form-item label="类别"><el-input v-model="newIngredient.category" placeholder="例如肉类、蔬菜、粮油" /></el-form-item>
      <el-form-item label="计量单位"><el-input v-model="newIngredient.unit" placeholder="例如kg、袋、瓶" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="ingredientDialog=false">取消</el-button><el-button type="primary" :loading="savingIngredient" @click="saveIngredient">{{ continuePlanAfterIngredient ? '保存食材并创建计划' : '添加并选中' }}</el-button></template>
  </el-dialog>
</template>
