<script setup lang="ts">
import { display, displayAuditDetail } from '../display'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { roleLabels } from '../roleAccess'

type Row = Record<string, any>
const users = ref<Row[]>([])
const canteens = ref<Row[]>([])
const stalls = ref<Row[]>([])
const templates = ref<Row[]>([])
const templateItems = ref<Row[]>([])
const audits = ref<Row[]>([])
const currentTemplateId = ref<number | null>(null)
const staffRoles = Object.fromEntries(Object.entries(roleLabels).filter(([code]) => code !== 'COMMUNITY'))
const userDialog = ref(false)
const accessDialog = ref(false)
const userForm = reactive({ username: '', password: '', displayName: '', role: 'STAFF', canteenIds: [] as number[] })
const accessForm = reactive({ id: 0, roles: [] as string[], canteenIds: [] as number[] })
const riskConfig = reactive({ weights: '' as string, thresholds: '' as string, version: 0 })

async function load() {
  const [u, c, s, t, a, risk] = await Promise.all([
    api.get('/admin/users'), api.get('/canteens'), api.get('/stalls'),
    api.get('/check-templates'), api.get('/admin/audit'), api.get('/admin/risk-config')
  ])
  users.value = u.data; canteens.value = c.data; stalls.value = s.data
  templates.value = t.data; audits.value = a.data
  riskConfig.version = Number(risk.data.version)
  riskConfig.weights = JSON.stringify(typeof risk.data.weights_json === 'string' ? JSON.parse(risk.data.weights_json) : risk.data.weights_json, null, 2)
  riskConfig.thresholds = JSON.stringify(typeof risk.data.thresholds_json === 'string' ? JSON.parse(risk.data.thresholds_json) : risk.data.thresholds_json, null, 2)
  if (currentTemplateId.value) await selectTemplate(currentTemplateId.value)
}

function openUser() {
  Object.assign(userForm, { username: '', password: '', displayName: '', role: 'STAFF', canteenIds: [] })
  userDialog.value = true
}
async function saveUser() {
  if (!userForm.username.trim() || !userForm.displayName.trim() || userForm.password.length < 8) {
    return ElMessage.warning('请填写账号、姓名和至少8位密码')
  }
  if (requiresCanteen([userForm.role]) && !userForm.canteenIds.length) return ElMessage.warning('请为食堂岗位选择负责食堂')
  await api.post('/admin/users', userForm)
  userDialog.value = false
  ElMessage.success('账号已创建')
  await load()
}
function openAccess(row: Row) {
  Object.assign(accessForm, { id: Number(row.id), roles: [...row.roles], canteenIds: [...row.canteenIds] })
  accessDialog.value = true
}
async function saveAccess() {
  if (!accessForm.roles.length) return ElMessage.warning('请至少选择一种角色')
  if (requiresCanteen(accessForm.roles) && !accessForm.canteenIds.length) return ElMessage.warning('请为食堂岗位选择负责食堂')
  await api.put(`/admin/users/${accessForm.id}/access`, { roles: accessForm.roles, canteenIds: accessForm.canteenIds })
  accessDialog.value = false
  ElMessage.success('权限已更新，账号重新登录后生效')
  await load()
}
function requiresCanteen(roles: string[]) {
  return roles.some(role => ['MANAGER', 'PURCHASER', 'CHEF', 'CLEANER', 'STAFF'].includes(role))
}
async function setUserActive(row: Row) {
  await api.put(`/admin/users/${row.id}/active`, { active: !row.active })
  await load()
}
async function resetPassword(row: Row) {
  const result = await ElMessageBox.prompt(`为 ${row.username} 设置新密码（8 至 64 位）`, '重置密码', { inputType: 'password', inputValidator: value => value.length >= 8 && value.length <= 64 || '密码需为8至64位' })
  await api.put(`/admin/users/${row.id}/password`, { password: result.value })
  ElMessage.success('密码已重置')
}
async function addCanteen() {
  const result = await ElMessageBox.prompt('输入食堂名称', '新增食堂', { inputValidator: value => !!value.trim() || '名称不能为空' })
  await api.post('/admin/canteens', { name: result.value })
  await load()
}
async function editCanteen(row: Row) {
  const name = (await ElMessageBox.prompt('食堂名称', '编辑食堂', { inputValue: row.name, inputValidator: value => !!value.trim() || '名称不能为空' })).value
  const location = (await ElMessageBox.prompt('所在位置', '编辑食堂', { inputValue: row.location || '' })).value
  await api.put(`/admin/canteens/${row.id}`, { name, location, active: Boolean(row.active) })
  await load()
}
async function toggleCanteen(row: Row) {
  await api.put(`/admin/canteens/${row.id}`, { name: row.name, location: row.location, active: !row.active })
  await load()
}
async function addStallFor(canteenId: number) {
  const result = await ElMessageBox.prompt('输入档口名称', '新增档口', { inputValidator: value => !!value.trim() || '名称不能为空' })
  await api.post('/admin/stalls', { canteenId, name: result.value })
  await load()
}
async function editStall(row: Row) {
  const name = (await ElMessageBox.prompt('档口名称', '编辑档口', { inputValue: row.name, inputValidator: value => !!value.trim() || '名称不能为空' })).value
  await api.put(`/admin/stalls/${row.id}`, { name, active: Boolean(row.active) })
  await load()
}
async function toggleStall(row: Row) {
  await api.put(`/admin/stalls/${row.id}`, { name: row.name, active: !row.active })
  await load()
}
async function selectTemplate(id: number) {
  currentTemplateId.value = id
  templateItems.value = (await api.get(`/check-templates/${id}/items`)).data
}
async function addTemplate() {
  const result = await ElMessageBox.prompt('输入模板名称', '新增检查模板', { inputValidator: value => !!value.trim() || '名称不能为空' })
  await api.post('/admin/check-templates', { name: result.value })
  await load()
}
async function addItem() {
  if (!currentTemplateId.value) return ElMessage.warning('请先选择模板')
  const result = await ElMessageBox.prompt('输入检查项目', '新增检查项', { inputValidator: value => !!value.trim() || '项目不能为空' })
  await api.post(`/admin/check-templates/${currentTemplateId.value}/items`, { title: result.value, sortOrder: templateItems.value.length + 1 })
  await selectTemplate(currentTemplateId.value)
}
async function setTemplateActive(row: Row) {
  await api.put(`/admin/check-templates/${row.id}/active`, { active: !row.active })
  await load()
}
async function setItemActive(row: Row) {
  await api.put(`/admin/check-items/${row.id}/active`, { active: !row.active })
  if (currentTemplateId.value) await selectTemplate(currentTemplateId.value)
}
async function saveRiskConfig() {
  let weights: unknown
  let thresholds: unknown
  try { weights = JSON.parse(riskConfig.weights); thresholds = JSON.parse(riskConfig.thresholds) }
  catch { return ElMessage.warning('风险参数必须是有效 JSON') }
  await ElMessageBox.confirm('新参数会影响后续风险评分和排程排序。确定保存？', '确认修改')
  await api.put('/admin/risk-config', { weights, thresholds })
  ElMessage.success('参数版本已更新')
  await load()
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>系统管理</h1><p>账号权限、食堂档口、检查模板、评分参数和操作记录</p></div><el-button @click="load">刷新</el-button></div>
  <el-tabs type="border-card">
    <el-tab-pane label="账号与权限">
      <div class="section-heading"><h2>账号列表</h2><el-button type="primary" @click="openUser">创建工作账号</el-button></div>
      <el-table :data="users" stripe><el-table-column prop="username" label="账号" min-width="115" /><el-table-column prop="display_name" label="姓名" min-width="110" /><el-table-column label="角色" min-width="175"><template #default="scope">{{ (scope.row.roles as string[]).map(r => roleLabels[r as keyof typeof roleLabels] || r).join('、') }}</template></el-table-column><el-table-column label="食堂范围" min-width="160"><template #default="scope">{{ (scope.row.canteenIds as number[]).map(id => canteens.find(c => c.id === id)?.name || id).join('、') || '全校/无' }}</template></el-table-column><el-table-column label="来源" width="95"><template #default="scope">{{ scope.row.self_registered ? '自行注册' : '管理员创建' }}</template></el-table-column><el-table-column label="状态" width="75"><template #default="scope"><el-tag :type="scope.row.active ? 'success' : 'info'">{{ scope.row.active ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="210"><template #default="scope"><el-button link @click="openAccess(scope.row)">权限</el-button><el-button link @click="resetPassword(scope.row)">重置密码</el-button><el-button link :type="scope.row.active ? 'danger' : 'success'" @click="setUserActive(scope.row)">{{ scope.row.active ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
    </el-tab-pane>
    <el-tab-pane label="食堂与档口">
      <div class="section-heading"><h2>食堂</h2><el-button type="primary" @click="addCanteen">新增食堂</el-button></div>
      <el-table :data="canteens" stripe><el-table-column prop="name" label="名称" /><el-table-column prop="location" label="位置" /><el-table-column label="状态" width="85"><template #default="scope">{{ scope.row.active ? '启用' : '停用' }}</template></el-table-column><el-table-column label="档口"><template #default="scope">{{ stalls.filter(s => s.canteen_id === scope.row.id).map(s => s.name).join('、') || '暂无' }}</template></el-table-column><el-table-column label="操作" width="220"><template #default="scope"><el-button link @click="editCanteen(scope.row)">编辑</el-button><el-button link @click="toggleCanteen(scope.row)">{{ scope.row.active ? '停用' : '启用' }}</el-button><el-button link @click="addStallFor(scope.row.id)">新增档口</el-button></template></el-table-column></el-table>
      <h3>档口</h3><el-table :data="stalls" stripe><el-table-column prop="canteen_name" label="食堂" /><el-table-column prop="name" label="档口" /><el-table-column label="状态"><template #default="scope">{{ scope.row.active ? '启用' : '停用' }}</template></el-table-column><el-table-column label="操作" width="140"><template #default="scope"><el-button link @click="editStall(scope.row)">编辑</el-button><el-button link @click="toggleStall(scope.row)">{{ scope.row.active ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
    </el-tab-pane>
    <el-tab-pane label="检查模板">
      <div class="two-col"><div><div class="section-heading"><h2>模板</h2><el-button @click="addTemplate">新增模板</el-button></div><el-table :data="templates" highlight-current-row @row-click="(row: Row) => selectTemplate(Number(row.id))"><el-table-column prop="name" label="名称" /><el-table-column label="状态" width="75"><template #default="scope">{{ scope.row.active ? '启用' : '停用' }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="scope"><el-button link @click.stop="setTemplateActive(scope.row)">{{ scope.row.active ? '停用' : '启用' }}</el-button></template></el-table-column></el-table></div><div><div class="section-heading"><h2>检查项</h2><el-button :disabled="!currentTemplateId" @click="addItem">新增项目</el-button></div><el-table :data="templateItems"><el-table-column prop="sort_order" label="序号" width="70" /><el-table-column prop="title" label="项目" /><el-table-column label="操作" width="90"><template #default="scope"><el-button link @click="setItemActive(scope.row)">{{ scope.row.active ? '停用' : '启用' }}</el-button></template></el-table-column></el-table></div></div>
    </el-tab-pane>
    <el-tab-pane label="风险参数"><p class="muted">当前版本：{{ riskConfig.version }}。六项权重总和应为 1，风险阈值为 0 至 100。</p><div class="two-col"><el-form-item label="指标权重 JSON"><el-input v-model="riskConfig.weights" type="textarea" :rows="10" /></el-form-item><el-form-item label="风险阈值 JSON"><el-input v-model="riskConfig.thresholds" type="textarea" :rows="10" /></el-form-item></div><el-button type="primary" @click="saveRiskConfig">保存新版本</el-button></el-tab-pane>
    <el-tab-pane label="操作日志"><el-table :data="audits" stripe height="520"><el-table-column prop="created_at" label="时间" min-width="160"><template #default="scope">{{ display(scope.row.created_at) }}</template></el-table-column><el-table-column prop="actor_name" label="操作者" min-width="115" /><el-table-column prop="action" label="动作" min-width="180"><template #default="scope">{{ display(scope.row.action) }}</template></el-table-column><el-table-column prop="target_type" label="对象类型" min-width="130"><template #default="scope">{{ display(scope.row.target_type) }}</template></el-table-column><el-table-column prop="target_id" label="对象编号" width="95" /><el-table-column prop="detail" label="记录内容" min-width="240" show-overflow-tooltip><template #default="scope">{{ displayAuditDetail(scope.row.detail) }}</template></el-table-column></el-table></el-tab-pane>
  </el-tabs>
  <el-dialog v-model="userDialog" title="创建工作账号" width="550px"><el-form label-position="top" class="form-grid"><el-form-item label="登录账号"><el-input v-model="userForm.username" /></el-form-item><el-form-item label="姓名"><el-input v-model="userForm.displayName" /></el-form-item><el-form-item label="初始密码"><el-input v-model="userForm.password" type="password" show-password /></el-form-item><el-form-item label="角色"><el-select v-model="userForm.role" style="width:100%"><el-option v-for="(label, code) in staffRoles" :key="code" :label="label" :value="code" /></el-select></el-form-item><el-form-item class="wide" label="负责食堂（总管、采购员、厨师、保洁等必选）"><el-select v-model="userForm.canteenIds" multiple style="width:100%"><el-option v-for="c in canteens" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="userDialog=false">取消</el-button><el-button type="primary" @click="saveUser">创建</el-button></template></el-dialog>
  <el-dialog v-model="accessDialog" title="编辑账号权限" width="520px"><el-form label-position="top"><el-form-item label="角色"><el-select v-model="accessForm.roles" multiple style="width:100%"><el-option v-for="(label, code) in roleLabels" :key="code" :label="label" :value="code" /></el-select></el-form-item><el-form-item label="负责食堂"><el-select v-model="accessForm.canteenIds" multiple style="width:100%"><el-option v-for="c in canteens" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="accessDialog=false">取消</el-button><el-button type="primary" @click="saveAccess">保存</el-button></template></el-dialog>
</template>
