<script setup lang="ts">
import { display, batchNumber } from '../display'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { useAuth } from '../store'
import { businessDate, todayInSchool } from '../businessDate'

const auth = useAuth()
const router = useRouter()
const dishes = ref<Record<string, unknown>[]>([])
const menus = ref<Record<string, unknown>[]>([])
const canteens = ref<Record<string, unknown>[]>([])
const ingredients = ref<Record<string, unknown>[]>([])
const batches = ref<Record<string, unknown>[]>([])
const dishDialog = ref(false)
const usageDialog = ref(false)
const recipeDialog = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const recipeDish = ref<Record<string, unknown> | null>(null)
const recipeIngredients = ref<Record<string, unknown>[]>([])
const selectedMenu = ref<Record<string, unknown> | null>(null)
const usageIngredientIds = ref<number[]>([])
const dishForm = reactive({ canteenId: 0, name: '', description: '' })
const usageForm = reactive({ batchId: 0, quantity: 1 })
const recipeIngredientId = ref(0)
const usableBatches = computed(() => batches.value.filter(row => row.status === 'ACCEPTED'
  && Number(row.canteen_id) === Number(selectedMenu.value?.canteen_id)
  && Number(row.available_quantity) > 0 && usageIngredientIds.value.includes(Number(row.ingredient_id))
  && (!row.expires_on || businessDate(row.expires_on) >= todayInSchool())))

async function load() {
  const [d, m, c, i, b] = await Promise.all([
    api.get('/catalog/dishes'), api.get('/catalog/menus'), api.get('/canteens'),
    auth.isManager ? api.get('/catalog/ingredients') : Promise.resolve({ data: [] }), api.get('/catalog/batches')
  ])
  dishes.value = d.data; menus.value = m.data; canteens.value = c.data; ingredients.value = i.data; batches.value = b.data
  const first = auth.profile?.canteenIds[0] ?? 0
  if (!dishForm.canteenId) dishForm.canteenId = first
}

async function saveDish() {
  if (!dishForm.canteenId || !dishForm.name.trim()) return ElMessage.warning('请选择食堂并填写菜品名称')
  await api.post('/catalog/dishes', dishForm)
  dishDialog.value = false
  dishForm.name = ''; dishForm.description = ''
  await load()
  ElMessage.success('已加入菜品目录；请到“每日食谱编排”选择日期和餐次，将菜品加入当天食谱')
}

async function openRecipe(row: Record<string, unknown>) {
  recipeDish.value = row
  recipeIngredients.value = (await api.get(`/catalog/dishes/${row.id}/ingredients`)).data
  recipeDialog.value = true
}

async function addRecipeIngredient() {
  if (!recipeIngredientId.value || !recipeDish.value) return
  recipeIngredients.value = (await api.post(`/catalog/dishes/${recipeDish.value.id}/ingredients`, { ingredientId: recipeIngredientId.value })).data
  recipeIngredientId.value = 0
}

async function openUsage(row: Record<string, unknown>) {
  selectedMenu.value = row
  usageForm.batchId = 0
  usageForm.quantity = 1
  const recipe = (await api.get(`/catalog/dishes/${row.dish_id}/ingredients`)).data as Record<string, unknown>[]
  usageIngredientIds.value = recipe.map(item => Number(item.id))
  usageDialog.value = true
}
async function saveUsage() {
  if (!selectedMenu.value || !usageForm.batchId) return ElMessage.warning('请选择合格食材批次')
  await api.post(`/catalog/menus/${selectedMenu.value.id}/usages`, usageForm)
  usageDialog.value = false
  ElMessage.success('实际用料已记录')
  await load()
}

async function serve(row: Record<string, unknown>) {
  await api.post(`/catalog/menus/${row.id}/serve`)
  ElMessage.success('已确认供餐')
  await load()
}

async function cancelMenu(row: Record<string, unknown>) {
  const result = await ElMessageBox.prompt('说明撤销原因。已登记实际用料的菜单不能撤销。', '撤销计划菜单',
    { inputType: 'textarea', inputPattern: /\S+/, inputErrorMessage: '必须填写撤销原因' })
  await api.post(`/catalog/menus/${row.id}/cancel`, { reason: result.value })
  ElMessage.success('菜单已撤销，历史记录保留')
  await load()
}

async function showTrace(row: Record<string, unknown>) {
  detail.value = (await api.get(`/trace/menus/${row.id}`)).data
}

onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>{{ auth.isManager ? '菜品目录与供餐进度' : auth.isChef ? '厨房用料与供餐' : '菜品与供餐' }}</h1><p>{{ auth.isManager ? '总管维护菜品和原料清单，查看厨师制作与供餐进度；加入菜品目录后仍需编排每日食谱。' : '根据已发布食谱登记实际食材批次，制作完成后确认供餐。' }}</p></div><div class="actions"><el-button v-if="auth.isManager" @click="dishDialog=true">新增目录菜品</el-button><el-button v-if="auth.isManager" type="primary" @click="router.push('/daily-menu')">编排每日食谱</el-button><el-button v-else-if="auth.isChef" @click="router.push('/daily-menu')">查看每日食谱 / 确认制作</el-button></div></div>
  <div class="panel"><div class="section-heading"><h2>供餐记录</h2><span class="muted">每日食谱发布、厨师确认制作并登记实际批次后，才能确认供餐</span></div><el-table :data="menus" stripe empty-text="暂无供餐记录"><el-table-column prop="service_date" label="日期" width="120"><template #default="scope">{{ businessDate(scope.row.service_date) }}</template></el-table-column><el-table-column prop="meal" label="餐次" width="90"><template #default="scope">{{ display(scope.row.meal) }}</template></el-table-column><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="stall_name" label="档口" /><el-table-column prop="dish_name" label="菜品" /><el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'SERVED' ? 'success' : 'info'">{{ scope.row.status === 'SERVED' ? '已供餐' : scope.row.status === 'CANCELLED' ? '已撤销' : scope.row.prepared_at ? '已制作' : scope.row.published_at ? '待制作' : '待发布' }}</el-tag></template></el-table-column><el-table-column label="操作" width="245"><template #default="scope"><el-button link @click="showTrace(scope.row)">用料追溯</el-button><el-button v-if="auth.isChef && scope.row.status === 'PLANNED' && scope.row.published_at" link type="primary" @click="openUsage(scope.row)">登记批次</el-button><el-button v-if="auth.isChef && scope.row.status === 'PLANNED' && scope.row.prepared_at" link type="success" @click="serve(scope.row)">确认供餐</el-button><el-button v-if="auth.isManager && scope.row.status === 'PLANNED'" link type="danger" @click="cancelMenu(scope.row)">撤销</el-button></template></el-table-column></el-table></div>
  <div class="panel"><div class="section-heading"><h2>菜品目录</h2><span class="muted">原料清单用于校验批次领用</span></div><el-table :data="dishes" stripe><el-table-column prop="name" label="菜品" /><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="description" label="说明" /><el-table-column label="原料" width="110"><template #default="scope"><el-button link type="primary" @click="openRecipe(scope.row)">{{ auth.isManager ? '查看 / 维护' : '查看' }}</el-button></template></el-table-column></el-table></div>

  <el-dialog v-model="dishDialog" title="新增菜品" width="510px"><el-form label-position="top"><el-form-item label="食堂"><el-select v-model="dishForm.canteenId" style="width:100%"><el-option v-for="c in canteens.filter(x => auth.profile?.canteenIds.includes(Number(x.id)))" :key="String(c.id)" :label="String(c.name)" :value="Number(c.id)" /></el-select></el-form-item><el-form-item label="菜品名称"><el-input v-model="dishForm.name" /></el-form-item><el-form-item label="说明"><el-input v-model="dishForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="dishDialog=false">取消</el-button><el-button type="primary" @click="saveDish">保存</el-button></template></el-dialog>
  <el-dialog v-model="recipeDialog" :title="`${recipeDish?.name || ''} · 原料清单`" width="520px"><el-table :data="recipeIngredients"><el-table-column prop="name" label="食材" /><el-table-column prop="unit" label="单位" /></el-table><div v-if="auth.isManager" class="inline-form" style="margin-top:15px"><el-select v-model="recipeIngredientId" filterable placeholder="选择食材" style="flex:1"><el-option v-for="i in ingredients" :key="String(i.id)" :label="String(i.name)" :value="Number(i.id)" /></el-select><el-button type="primary" @click="addRecipeIngredient">添加</el-button></div></el-dialog>
  <el-dialog v-model="usageDialog" :title="`登记实际批次 · ${selectedMenu?.dish_name || ''}`" width="520px"><el-form label-position="top"><el-form-item label="合格批次"><el-select v-model="usageForm.batchId" filterable no-data-text="暂无符合本菜原料清单的合格库存" style="width:100%"><el-option v-for="b in usableBatches" :key="String(b.id)" :label="`批次 ${b.id} · ${b.ingredient_name} · 可用 ${b.available_quantity} ${b.ingredient_unit}`" :value="Number(b.id)" /></el-select></el-form-item><el-form-item label="使用量"><el-input-number v-model="usageForm.quantity" :min="0.001" :precision="3" style="width:100%" /></el-form-item></el-form><template #footer><el-button @click="usageDialog=false">取消</el-button><el-button type="primary" @click="saveUsage">记录用料</el-button></template></el-dialog>
  <el-dialog :model-value="Boolean(detail)" @close="detail=null" title="供餐用料反向追溯" width="730px"><template v-if="detail"><el-alert v-for="w in detail.warnings as string[]" :key="w" :title="w" type="warning" :closable="false" style="margin-bottom:12px" /><p>供餐记录：{{ businessDate((detail.menu as Record<string, unknown>)?.service_date) }} / {{ display((detail.menu as Record<string, unknown>)?.meal) }}</p><el-table :data="detail.ingredients as object[]"><el-table-column label="食材"><template #default="scope">{{ scope.row.usage.ingredient_name }}</template></el-table-column><el-table-column label="批次编号"><template #default="scope">{{ batchNumber(scope.row.batch) }}</template></el-table-column><el-table-column label="供应商"><template #default="scope">{{ scope.row.batch.supplier_name }}</template></el-table-column><el-table-column label="使用量"><template #default="scope">{{ scope.row.usage.quantity }}</template></el-table-column></el-table></template></el-dialog>
</template>
