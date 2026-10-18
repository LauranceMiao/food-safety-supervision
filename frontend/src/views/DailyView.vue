<script setup lang="ts">
import { display } from '../display'
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useAuth } from '../store'

const auth = useAuth()
const rows = ref<Record<string, unknown>[]>([])
const canteens = ref<Record<string, unknown>[]>([])
const templates = ref<Record<string, unknown>[]>([])
const items = ref<Record<string, unknown>[]>([])
const detail = ref<Record<string, unknown> | null>(null)
const dialog = ref(false)
const form = reactive({ canteenId: 0, templateId: 1, checkDate: new Date().toISOString().slice(0, 10), note: '', results: [] as { itemId: number; result: string; note: string }[] })

async function load() {
  const [a, b, c] = await Promise.all([api.get('/daily-checks'), api.get('/canteens'), api.get('/check-templates')])
  rows.value = a.data; canteens.value = b.data; templates.value = c.data
  form.canteenId ||= auth.profile?.canteenIds[0] ?? 0
}

async function loadTemplate(id: number) {
  items.value = (await api.get(`/check-templates/${id}/items`)).data.filter((item: Record<string, unknown>) => item.active)
  form.results = items.value.map(item => ({ itemId: Number(item.id), result: 'PASS', note: '' }))
}
watch(() => form.templateId, loadTemplate)

async function openCreate() { await loadTemplate(form.templateId); dialog.value = true }
async function save() {
  if (!form.canteenId) return ElMessage.warning('请选择食堂')
  await api.post('/daily-checks', form)
  dialog.value = false
  ElMessage.success('每日检查已提交')
  await load()
}
async function show(id: number) { detail.value = (await api.get(`/daily-checks/${id}`)).data }
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>日常管控</h1><p>逐项记录每日食品安全检查，异常项需说明</p></div><el-button v-if="auth.isManager" type="primary" @click="openCreate">填写今日检查</el-button></div>
  <div class="panel"><el-table :data="rows" stripe><el-table-column prop="check_date" label="检查日期"><template #default="scope">{{ display(scope.row.check_date) }}</template></el-table-column><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="checker_name" label="检查人" /><el-table-column prop="note" label="备注" /><el-table-column label="操作" width="90"><template #default="scope"><el-button link type="primary" @click="show(scope.row.id)">查看</el-button></template></el-table-column></el-table></div>
  <el-dialog v-model="dialog" title="每日食品安全检查" width="680px"><el-form label-position="top" class="form-grid"><el-form-item label="食堂"><el-select v-model="form.canteenId" style="width:100%"><el-option v-for="c in canteens.filter(x => auth.profile?.canteenIds.includes(Number(x.id)))" :key="String(c.id)" :label="String(c.name)" :value="Number(c.id)" /></el-select></el-form-item><el-form-item label="检查日期"><el-date-picker v-model="form.checkDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="检查模板" class="wide"><el-select v-model="form.templateId" style="width:100%"><el-option v-for="t in templates" :key="String(t.id)" :label="String(t.name)" :value="Number(t.id)" /></el-select></el-form-item></el-form><div v-for="(item, index) in items" :key="String(item.id)" class="panel" style="margin-bottom:10px;padding:12px"><strong>{{ item.title }}</strong><div class="inline-form" style="margin-top:8px"><el-radio-group v-model="form.results[index]!.result"><el-radio value="PASS">正常</el-radio><el-radio value="FAIL">异常</el-radio><el-radio value="NA">不适用</el-radio></el-radio-group><el-input v-model="form.results[index]!.note" placeholder="异常时填写情况" style="flex:1;min-width:180px" /></div></div><el-form-item label="总体备注"><el-input v-model="form.note" type="textarea" /></el-form-item><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">提交</el-button></template></el-dialog>
  <el-dialog :model-value="Boolean(detail)" @close="detail=null" title="日常管控详情" width="650px"><template v-if="detail"><p>{{ display(detail.check_date) }} · 食堂编号 {{ detail.canteen_id }}</p><el-table :data="detail.results as object[]"><el-table-column prop="item_title" label="项目" /><el-table-column prop="result" label="结果" width="100"><template #default="scope">{{ display(scope.row.result) }}</template></el-table-column><el-table-column prop="note" label="说明" /></el-table></template></el-dialog>
</template>
