<script setup lang="ts">
import { display } from '../display'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, toDateTime } from '../api'
import { useAuth } from '../store'
import { todayInSchool } from '../businessDate'

type Row = Record<string, any>
const auth = useAuth()
const rows = ref<Row[]>([])
const canteens = ref<Row[]>([])
const dialog = ref(false)
const form = reactive({ canteenId: 0, category: 'SAMPLE', operationDate: todayInSchool(), meal: 'LUNCH' })
const details = reactive({ dishName: '', quantityGrams: 125, storageLocation: '', equipment: '', area: '',
  method: '', durationMinutes: 30, sampledAt: new Date(), completedAt: new Date(), note: '' })
const categoryNames: Record<string, string> = { SAMPLE: '食品留样', DISINFECTION: '餐饮具消毒', CLEANING: '环境清洁' }
const writableCategories = computed(() => [
  ...(auth.isChef ? ['SAMPLE'] : []), ...(auth.isCleaner ? ['CLEANING', 'DISINFECTION'] : [])
])
const visibleRows = computed(() => auth.isManager ? rows.value : rows.value.filter(row => writableCategories.value.includes(String(row.category))))
const pageTitle = computed(() => auth.isManager ? '留样与卫生执行记录' : auth.isChef && auth.isCleaner ? '食品留样与清洁消毒' : auth.isChef ? '食品留样' : '清洁与消毒')
const pageDescription = computed(() => auth.isManager ? '查看厨师的食品留样及保洁员的清洁消毒记录，监督现场执行情况。' : auth.isChef ? '记录本食堂菜品的留样数量、存放位置和时间。' : '记录本食堂环境清洁与餐饮具消毒的区域、方式及完成时间。')
const fieldNames: Record<string, string> = {
  dishName: '留样菜品', quantityGrams: '留样克数', storageLocation: '存放位置', sampledAt: '留样时间',
  equipment: '餐饮具或设备', method: '处理方式', durationMinutes: '持续分钟',
  area: '清洁区域', completedAt: '完成时间'
}

async function load() {
  const [a, b] = await Promise.all([api.get('/operations'), api.get('/canteens')])
  rows.value = a.data; canteens.value = b.data
  form.canteenId ||= auth.profile?.canteenIds[0] ?? 0
}

function summary(row: Row) {
  try {
    const d = typeof row.details_json === 'string' ? JSON.parse(row.details_json) : row.details_json
    return Object.entries(d).filter(([key, value]) => key !== 'note' && value)
      .map(([key, value]) => `${fieldNames[key] || key}：${display(value)}`).join('；')
  } catch { return '无法解析记录' }
}

async function save() {
  if (!form.canteenId) return ElMessage.warning('请选择食堂')
  if (!writableCategories.value.includes(form.category)) return ElMessage.warning('当前岗位不能登记该类记录')
  const content = form.category === 'SAMPLE'
    ? { dishName: details.dishName, quantityGrams: details.quantityGrams, storageLocation: details.storageLocation, sampledAt: toDateTime(details.sampledAt), note: details.note }
    : form.category === 'DISINFECTION'
      ? { equipment: details.equipment, method: details.method, durationMinutes: details.durationMinutes, completedAt: toDateTime(details.completedAt), note: details.note }
      : { area: details.area, method: details.method, completedAt: toDateTime(details.completedAt), note: details.note }
  await api.post('/operations', { ...form, details: content })
  dialog.value = false
  ElMessage.success('记录已保存')
  await load()
}
function open() {
  form.category = writableCategories.value[0] || 'SAMPLE'
  form.operationDate = todayInSchool()
  form.meal = form.category === 'SAMPLE' ? 'LUNCH' : 'NONE'
  Object.assign(details, { dishName: '', quantityGrams: 125, storageLocation: '', equipment: '', area: '', method: '', durationMinutes: 30, sampledAt: new Date(), completedAt: new Date(), note: '' })
  dialog.value = true
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>{{ pageTitle }}</h1><p>{{ pageDescription }}</p></div><el-button v-if="writableCategories.length" type="primary" @click="open">新增记录</el-button></div>
  <div class="panel"><el-table :data="visibleRows" stripe empty-text="暂无记录"><el-table-column prop="operation_date" label="日期" width="120"><template #default="scope">{{ display(scope.row.operation_date) }}</template></el-table-column><el-table-column prop="canteen_name" label="食堂" width="120" /><el-table-column label="类型" width="130"><template #default="scope">{{ categoryNames[scope.row.category] }}</template></el-table-column><el-table-column prop="meal" label="餐次" width="95"><template #default="scope">{{ display(scope.row.meal) }}</template></el-table-column><el-table-column label="记录内容" min-width="280" show-overflow-tooltip><template #default="scope">{{ summary(scope.row) }}</template></el-table-column><el-table-column prop="recorder_name" label="记录人" width="110" /><el-table-column prop="created_at" label="录入时间" min-width="160"><template #default="scope">{{ display(scope.row.created_at) }}</template></el-table-column></el-table></div>
  <el-dialog v-model="dialog" title="新增现场记录" width="650px"><el-form label-position="top" class="form-grid"><el-form-item label="食堂"><el-select v-model="form.canteenId" style="width:100%"><el-option v-for="c in canteens.filter(c => auth.profile?.canteenIds.includes(Number(c.id)))" :key="c.id" :label="c.name" :value="Number(c.id)" /></el-select></el-form-item><el-form-item label="记录类型"><el-select v-model="form.category" style="width:100%"><el-option v-for="key in writableCategories" :key="key" :label="categoryNames[key]" :value="key" /></el-select></el-form-item><el-form-item label="日期"><el-date-picker v-model="form.operationDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="餐次"><el-select v-model="form.meal" style="width:100%"><el-option label="早餐" value="BREAKFAST" /><el-option label="午餐" value="LUNCH" /><el-option label="晚餐" value="DINNER" /><el-option label="不适用" value="NONE" /></el-select></el-form-item>
    <template v-if="form.category === 'SAMPLE'"><el-form-item label="留样菜品"><el-input v-model="details.dishName" /></el-form-item><el-form-item label="留样数量（克）"><el-input-number v-model="details.quantityGrams" :min="1" style="width:100%" /></el-form-item><el-form-item label="存放位置"><el-input v-model="details.storageLocation" /></el-form-item><el-form-item label="留样时间"><el-date-picker v-model="details.sampledAt" type="datetime" style="width:100%" /></el-form-item></template>
    <template v-if="form.category === 'DISINFECTION'"><el-form-item label="餐饮具或设备"><el-input v-model="details.equipment" /></el-form-item><el-form-item label="消毒方式"><el-input v-model="details.method" /></el-form-item><el-form-item label="持续分钟数"><el-input-number v-model="details.durationMinutes" :min="1" style="width:100%" /></el-form-item><el-form-item label="完成时间"><el-date-picker v-model="details.completedAt" type="datetime" style="width:100%" /></el-form-item></template>
    <template v-if="form.category === 'CLEANING'"><el-form-item label="清洁区域"><el-input v-model="details.area" /></el-form-item><el-form-item label="清洁方式"><el-input v-model="details.method" /></el-form-item><el-form-item label="完成时间"><el-date-picker v-model="details.completedAt" type="datetime" style="width:100%" /></el-form-item></template>
    <el-form-item class="wide" label="补充说明"><el-input v-model="details.note" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存记录</el-button></template></el-dialog>
</template>
