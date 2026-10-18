<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const props = defineProps<{ modelValue: string; purpose: string; canteenId: number; imageOnly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const busy = ref(false)

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 8 * 1024 * 1024) { ElMessage.warning('附件不能超过 8 MB'); input.value = ''; return }
  if (props.purpose !== 'SUPPLIER_CERT' && !props.canteenId) { ElMessage.warning('请先选择食堂'); input.value = ''; return }
  const body = new FormData()
  body.append('file', file)
  body.append('purpose', props.purpose)
  body.append('canteenId', String(props.canteenId))
  busy.value = true
  try {
    const result = (await api.post('/files', body)).data as { path: string }
    emit('update:modelValue', result.path)
    ElMessage.success('附件已上传')
  } finally { busy.value = false; input.value = '' }
}
</script>

<template>
  <div class="inline-form">
    <input type="file" :accept="imageOnly ? 'image/png,image/jpeg,image/webp' : 'image/png,image/jpeg,image/webp,application/pdf'" :disabled="busy" @change="upload" />
    <a v-if="modelValue" :href="modelValue" target="_blank" rel="noopener noreferrer">查看附件</a>
    <el-button v-if="modelValue" link type="danger" @click="emit('update:modelValue', '')">移除</el-button>
    <span class="muted">{{ busy ? '上传中…' : 'PNG、JPG、WebP 或 PDF，最多 8 MB' }}</span>
  </div>
</template>
