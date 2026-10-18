<script setup lang="ts">
import { display } from '../display'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import FileUpload from '../components/FileUpload.vue'
import { useAuth } from '../store'

const auth = useAuth()
const suppliers = ref<Record<string, unknown>[]>([])
const ingredients = ref<Record<string, unknown>[]>([])
const dialog = ref(false)
const editingId = ref<number | null>(null)
const ingredientDialog = ref(false)
const form = reactive({ name: '', licenseNo: '', licenseExpiresOn: '', contactName: '', contactPhone: '', certificatePath: '', active: true })
const ingredient = reactive({ name: '', category: '', unit: 'kg' })

async function load() {
  const [s, i] = await Promise.all([api.get('/catalog/suppliers'), api.get('/catalog/ingredients')])
  suppliers.value = s.data
  ingredients.value = i.data
}

function openSupplier(row?: Record<string, unknown>) {
  editingId.value = row ? Number(row.id) : null
  Object.assign(form, { name: '', licenseNo: '', licenseExpiresOn: '', contactName: '', contactPhone: '', certificatePath: '', active: true })
  if (row) Object.assign(form, { name: row.name, licenseNo: row.license_no, licenseExpiresOn: row.license_expires_on || '', contactName: row.contact_name, contactPhone: row.contact_phone, certificatePath: row.certificate_path || '', active: Boolean(row.active) })
  dialog.value = true
}

async function saveSupplier() {
  if (!form.name.trim()) return ElMessage.warning('请填写供应商名称')
  if (editingId.value) await api.put(`/catalog/suppliers/${editingId.value}`, form)
  else await api.post('/catalog/suppliers', form)
  dialog.value = false
  ElMessage.success('已保存')
  await load()
}

async function saveIngredient() {
  if (!ingredient.name.trim() || !ingredient.unit.trim()) return ElMessage.warning('请填写食材名称和单位')
  await api.post('/catalog/ingredients', ingredient)
  ingredientDialog.value = false
  Object.assign(ingredient, { name: '', category: '', unit: 'kg' })
  ElMessage.success('食材已添加')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page-head"><div><h1>供应商与食材</h1><p>维护进货来源、证照有效期和食材目录</p></div><div v-if="auth.isManager" class="actions"><el-button @click="ingredientDialog=true">新增食材</el-button><el-button type="primary" @click="openSupplier()">新增供应商</el-button></div></div>
  <div class="panel"><div class="section-heading"><h2>供应商</h2><span class="muted">{{ suppliers.length }} 家</span></div>
    <el-table :data="suppliers" stripe><el-table-column prop="name" label="名称" min-width="160" /><el-table-column prop="license_no" label="许可证号" min-width="160" /><el-table-column prop="license_expires_on" label="证照到期" min-width="120"><template #default="scope">{{ display(scope.row.license_expires_on) }}</template></el-table-column><el-table-column prop="contact_name" label="联系人" /><el-table-column prop="contact_phone" label="电话" /><el-table-column label="证照" width="85"><template #default="scope"><a v-if="scope.row.certificate_path" :href="scope.row.certificate_path" target="_blank" rel="noopener noreferrer">查看</a></template></el-table-column><el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.active ? 'success' : 'info'">{{ scope.row.active ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column v-if="auth.isManager" label="操作" width="90"><template #default="scope"><el-button link type="primary" @click="openSupplier(scope.row)">编辑</el-button></template></el-table-column></el-table>
  </div>
  <div class="panel"><div class="section-heading"><h2>食材目录</h2><span class="muted">{{ ingredients.length }} 种</span></div><el-table :data="ingredients" stripe><el-table-column prop="name" label="食材" /><el-table-column prop="category" label="类别"><template #default="scope">{{ display(scope.row.category) }}</template></el-table-column><el-table-column prop="unit" label="计量单位" /></el-table></div>

  <el-dialog v-model="dialog" :title="editingId ? '编辑供应商' : '新增供应商'" width="560px"><el-form label-position="top" class="form-grid"><el-form-item label="供应商名称"><el-input v-model="form.name" /></el-form-item><el-form-item label="许可证号"><el-input v-model="form.licenseNo" /></el-form-item><el-form-item label="证照有效期"><el-date-picker v-model="form.licenseExpiresOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item><el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item><el-form-item label="状态"><el-switch v-model="form.active" /></el-form-item><el-form-item class="wide" label="证照附件"><FileUpload v-model="form.certificatePath" purpose="SUPPLIER_CERT" :canteen-id="0" /></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="saveSupplier">保存</el-button></template></el-dialog>
  <el-dialog v-model="ingredientDialog" title="新增食材" width="500px"><el-form label-position="top"><el-form-item label="名称"><el-input v-model="ingredient.name" /></el-form-item><el-form-item label="类别"><el-input v-model="ingredient.category" placeholder="例如蔬菜、肉类、粮油" /></el-form-item><el-form-item label="计量单位"><el-input v-model="ingredient.unit" /></el-form-item></el-form><template #footer><el-button @click="ingredientDialog=false">取消</el-button><el-button type="primary" @click="saveIngredient">保存</el-button></template></el-dialog>
</template>
