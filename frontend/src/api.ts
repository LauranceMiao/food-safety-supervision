import axios from 'axios'
import { ElMessage } from 'element-plus'

export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN'
})

api.interceptors.response.use(
  response => response,
  error => {
    const message = error.response?.data?.error || error.message || '请求失败'
    if (error.response?.status !== 401) ElMessage.error(message)
    return Promise.reject(error)
  }
)

export async function prepareCsrf() {
  await api.get('/auth/csrf')
}

export function toDateTime(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value)
  const parts = [date.getFullYear(), date.getMonth() + 1, date.getDate()]
  return `${parts[0]}-${String(parts[1]).padStart(2, '0')}-${String(parts[2]).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:00`
}
