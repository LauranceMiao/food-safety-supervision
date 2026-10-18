<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { useAuth } from '../store'
import { dailyMenuRows, todayInSchool } from '../businessDate'
import { display } from '../display'

type Row = Record<string, any>
type MenuItem = { meal: string; dishId: number; stallId: number | null; targetPortions: number | null; cookingNote: string }

const auth = useAuth()
const today = todayInSchool()
const serviceDate = ref(today)
const canteenId = ref(0)
const canteens = ref<Row[]>([])
const dishes = ref<Row[]>([])
const stalls = ref<Row[]>([])
const menus = ref<Row[]>([])
const createDialog = ref(false)
const editingId = ref<number | null>(null)
const detail = ref<Row | null>(null)
const recipeIngredients = ref<Row[]>([])
const saving = ref(false)
const publishing = ref(false)
const items = reactive<MenuItem[]>([])

const scopedCanteens = computed(() => canteens.value.filter(row => auth.profile?.canteenIds.includes(Number(row.id))))
const selectedMenus = computed(() => dailyMenuRows(menus.value, canteenId.value, serviceDate.value))
const drafts = computed(() => selectedMenus.value.filter(row => !row.published_at))
const canConfirmCooking = computed(() => auth.isChef)

function mealName(value: string) {
  return ({ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' } as Record<string, string>)[value] || value
}

function statusName(row: Row) {
  if (row.status === 'SERVED') return '已供餐'
  if (row.prepared_at) return '已制作'
  return row.published_at ? '待制作' : '待发布'
}

function newItem(): MenuItem {
  return { meal: 'LUNCH', dishId: 0, stallId: null, targetPortions: null, cookingNote: '' }
}

function openCreate() {
  editingId.value = null
  items.splice(0, items.length, newItem())
  createDialog.value = true
}

function openEdit(row: Row) {
  editingId.value = Number(row.id)
  items.splice(0, items.length, {
    meal: String(row.meal), dishId: Number(row.dish_id),
    stallId: row.stall_id == null ? null : Number(row.stall_id),
    targetPortions: row.target_portions == null ? null : Number(row.target_portions),
    cookingNote: String(row.cooking_note || '')
  })
  createDialog.value = true
}

async function load() {
  const [c, d, s, m] = await Promise.all([
    api.get('/canteens'), api.get('/catalog/dishes'), api.get('/stalls'), api.get('/catalog/menus')
  ])
  canteens.value = c.data; dishes.value = d.data; stalls.value = s.data; menus.value = m.data
  if (!scopedCanteens.value.some(row => Number(row.id) === canteenId.value)) {
    canteenId.value = Number(scopedCanteens.value[0]?.id || 0)
  }
}

async function saveDraft() {
  if (saving.value) return
  if (!canteenId.value || !serviceDate.value || !items.length || items.some(item => !item.dishId)) {
    return ElMessage.warning('请选择食堂、日期和每道菜品')
  }
  const keys = items.map(item => `${item.meal}/${item.dishId}/${item.stallId ?? 0}`)
  if (new Set(keys).size !== keys.length) return ElMessage.warning('同一餐次和档口不能重复安排同一道菜')
  saving.value = true
  try {
    if (editingId.value) await api.put(`/catalog/menus/${editingId.value}`, items[0])
    else await api.post('/catalog/menus/daily', { canteenId: canteenId.value, serviceDate: serviceDate.value, items })
    createDialog.value = false
    await load()
    ElMessage.success(editingId.value ? '食谱草稿已修改，已更新下方列表' : `已保存 ${items.length} 道菜并显示在下方列表；点击“发布食谱”后厨师才能看到`)
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (publishing.value || !drafts.value.length) return
  await ElMessageBox.confirm(`发布 ${serviceDate.value} 的 ${drafts.value.length} 道菜？发布后本食堂厨师即可查看。`, '发布每日食谱')
  publishing.value = true
  try {
    await api.post('/catalog/menus/daily/publish', { canteenId: canteenId.value, serviceDate: serviceDate.value })
    ElMessage.success('每日食谱已发布')
    await load()
  } finally {
    publishing.value = false
  }
}

async function prepare(row: Row) {
  await api.post(`/catalog/menus/${row.id}/prepare`)
  ElMessage.success(`${row.dish_name} 已记录为制作完成`)
  await load()
}

async function cancel(row: Row) {
  const answer = await ElMessageBox.prompt('请填写调整原因；已登记用料的菜品不能撤销。', '移除菜品',
    { inputPattern: /\S+/, inputErrorMessage: '请填写原因' })
  await api.post(`/catalog/menus/${row.id}/cancel`, { reason: answer.value })
  ElMessage.success('菜品已移除；如需替换，请重新添加并发布')
  await load()
}

async function showDetail(row: Row) {
  recipeIngredients.value = (await api.get(`/catalog/dishes/${row.dish_id}/ingredients`)).data
  detail.value = row
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div><h1>{{ auth.isManager ? '每日食谱编排' : '每日食谱 · 厨房工作单' }}</h1><p>{{ auth.isManager ? '选择菜品编排每日食谱，保存草稿后发布给厨师；总管跟进制作进度。' : '按总管发布的食谱制作，登记实际用料并确认制作、供餐。' }}</p></div>
    <div v-if="auth.isManager" class="actions"><el-button @click="$router.push('/menus')">维护菜品目录与原料</el-button><el-button type="primary" :disabled="!canteenId" @click="openCreate">添加到今日食谱</el-button></div>
  </div>

  <div class="panel daily-menu-toolbar">
    <el-select v-model="canteenId" placeholder="选择食堂" style="width:180px"><el-option v-for="c in scopedCanteens" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select>
    <el-date-picker v-model="serviceDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
    <el-button @click="serviceDate=today">今天</el-button>
    <el-button v-if="auth.isManager && drafts.length" type="success" :loading="publishing" @click="publish">发布食谱（{{ drafts.length }} 道待发布）</el-button>
  </div>

  <el-alert v-if="auth.isManager && !dishes.some(row => Number(row.canteen_id) === canteenId)" title="请先在“菜品与供餐”中建立菜品，再来编排每日食谱。" type="info" :closable="false" style="margin-bottom:16px" />
  <el-alert v-if="auth.isManager && drafts.length" :title="`下方有 ${drafts.length} 道食谱草稿；点击“发布食谱”后厨师才能看到。菜品目录仅作为编排时的备选菜品。`" type="info" :closable="false" style="margin-bottom:16px" />

  <div class="panel">
    <div class="section-heading"><h2>{{ serviceDate }} · {{ scopedCanteens.find(row => Number(row.id) === canteenId)?.name || '食堂' }}</h2><span class="muted">{{ selectedMenus.length }} 道菜</span></div>
    <el-table :data="selectedMenus" stripe :empty-text="auth.isManager ? '当天尚未安排食谱，请点击“添加到今日食谱”' : '当天尚无总管已发布的食谱'">
      <el-table-column label="餐次" width="90"><template #default="scope">{{ mealName(scope.row.meal) }}</template></el-table-column>
      <el-table-column prop="dish_name" label="菜品" min-width="150" />
      <el-table-column label="档口" min-width="100"><template #default="scope">{{ scope.row.stall_name || '未指定' }}</template></el-table-column>
      <el-table-column label="预计份数" width="95"><template #default="scope">{{ scope.row.target_portions || '未填写' }}</template></el-table-column>
      <el-table-column label="制作要求" min-width="180"><template #default="scope">{{ scope.row.cooking_note || '按菜品原料清单制作' }}</template></el-table-column>
      <el-table-column label="进度" width="105"><template #default="scope"><el-tag :type="scope.row.status === 'SERVED' ? 'success' : scope.row.prepared_at ? 'primary' : scope.row.published_at ? 'warning' : 'info'">{{ statusName(scope.row) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" min-width="225"><template #default="scope">
        <el-button link @click="showDetail(scope.row)">查看做法与原料</el-button>
        <el-button v-if="canConfirmCooking && scope.row.published_at && !scope.row.prepared_at && scope.row.status === 'PLANNED'" link type="primary" @click="prepare(scope.row)">确认已制作</el-button>
        <el-button v-if="auth.isManager && !scope.row.published_at && scope.row.status === 'PLANNED'" link @click="openEdit(scope.row)">修改</el-button>
        <el-button v-if="auth.isManager && scope.row.status === 'PLANNED'" link type="danger" @click="cancel(scope.row)">移除</el-button>
      </template></el-table-column>
    </el-table>
    <p class="muted">{{ auth.isManager ? '总管负责编排与发布；厨师负责实际用料、制作和供餐。已保存的草稿也会显示在本列表。' : '在“菜品与供餐”登记实际使用的合格食材批次，制作完成后确认制作，再确认供餐。' }}</p>
    <el-button @click="$router.push('/menus')">{{ auth.isManager ? '查看供餐进度与菜品目录' : '登记实际用料与供餐' }}</el-button>
  </div>

  <el-dialog v-model="createDialog" :title="editingId ? '修改食谱草稿' : `编排 ${serviceDate} 食谱`" width="760px">
    <p class="muted">{{ editingId ? '修改后仍是草稿，发布后厨师才能看到。' : '选择本食堂已有菜品，可一次安排多个餐次；保存为草稿后还需发布。' }}</p>
    <div v-for="(item, index) in items" :key="index" class="daily-menu-item">
      <div class="section-heading"><strong>第 {{ index + 1 }} 道菜</strong><el-button v-if="!editingId && items.length > 1" link type="danger" @click="items.splice(index,1)">移除</el-button></div>
      <el-form label-position="top" class="form-grid">
        <el-form-item label="餐次"><el-select v-model="item.meal" style="width:100%"><el-option label="早餐" value="BREAKFAST" /><el-option label="午餐" value="LUNCH" /><el-option label="晚餐" value="DINNER" /></el-select></el-form-item>
        <el-form-item label="菜品"><el-select v-model="item.dishId" filterable placeholder="选择菜品" style="width:100%"><el-option v-for="d in dishes.filter(row => Number(row.canteen_id) === canteenId)" :key="d.id" :label="d.name" :value="Number(d.id)" /></el-select></el-form-item>
        <el-form-item label="档口（可选）"><el-select v-model="item.stallId" clearable placeholder="未指定" style="width:100%"><el-option v-for="s in stalls.filter(row => Number(row.canteen_id) === canteenId)" :key="s.id" :label="s.name" :value="Number(s.id)" /></el-select></el-form-item>
        <el-form-item label="预计份数（可选）"><el-input-number v-model="item.targetPortions" :min="1" :max="100000" style="width:100%" /></el-form-item>
        <el-form-item class="wide" label="制作要求（可选）"><el-input v-model="item.cookingNote" placeholder="例如少盐、先焯水、午餐前完成" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
    </div>
    <el-button v-if="!editingId" :disabled="items.length >= 30" @click="items.push(newItem())">再加一道菜</el-button>
    <template #footer><el-button @click="createDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDraft">{{ editingId ? '保存修改' : '保存草稿' }}</el-button></template>
  </el-dialog>

  <el-dialog :model-value="Boolean(detail)" @close="detail=null" :title="`${detail?.dish_name || ''} · 制作参考`" width="520px">
    <template v-if="detail"><p><strong>餐次：</strong>{{ mealName(detail.meal) }}　<strong>预计份数：</strong>{{ detail.target_portions || '未填写' }}</p><p><strong>制作要求：</strong>{{ detail.cooking_note || '无补充要求' }}</p><p><strong>菜品说明：</strong>{{ dishes.find(row => Number(row.id) === Number(detail?.dish_id))?.description || '未填写' }}</p><h3>原料清单</h3><el-table :data="recipeIngredients" empty-text="总管尚未维护这道菜的原料清单"><el-table-column prop="name" label="食材" /><el-table-column prop="unit" label="单位" /></el-table><p v-if="detail.prepared_at" class="muted">制作确认：{{ detail.prepared_by_name || '厨师' }} · {{ display(detail.prepared_at) }}</p></template>
  </el-dialog>
</template>

<style scoped>
.daily-menu-toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
.daily-menu-item{border:1px solid #e5ede8;border-radius:10px;padding:15px;margin-bottom:14px}
</style>
