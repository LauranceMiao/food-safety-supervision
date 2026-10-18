import { defineStore } from 'pinia'
import { api, prepareCsrf } from './api'

export interface Profile {
  id: number
  username: string
  displayName: string
  roles: string[]
  canteenIds: number[]
}

export const useAuth = defineStore('auth', {
  state: () => ({ profile: null as Profile | null, loading: false }),
  getters: {
    isCommunity: state => !!state.profile?.roles.includes('COMMUNITY')
      && !state.profile.roles.some(role => ['ADMIN', 'PRINCIPAL', 'MANAGER', 'INSPECTOR', 'PURCHASER', 'CHEF', 'CLEANER', 'STAFF'].includes(role)),
    isInspector: state => state.profile?.roles.includes('INSPECTOR') ?? false,
    isManager: state => state.profile?.roles.includes('MANAGER') ?? false,
    isStaff: state => state.profile?.roles.includes('STAFF') ?? false,
    isPurchaser: state => state.profile?.roles.includes('PURCHASER') ?? false,
    isChef: state => state.profile?.roles.includes('CHEF') ?? false,
    isCleaner: state => state.profile?.roles.includes('CLEANER') ?? false,
    isWorker: state => state.profile?.roles.some(role => ['STAFF', 'PURCHASER', 'CHEF', 'CLEANER'].includes(role)) ?? false,
    isAdmin: state => state.profile?.roles.includes('ADMIN') ?? false,
    isPrincipal: state => state.profile?.roles.includes('PRINCIPAL') ?? false
  },
  actions: {
    async load() {
      this.loading = true
      try {
        await prepareCsrf()
        this.profile = (await api.get<Profile>('/auth/me')).data
      } catch {
        this.profile = null
      } finally {
        this.loading = false
      }
    },
    async login(username: string, password: string) {
      await prepareCsrf()
      const form = new URLSearchParams({ username, password })
      await api.post('/auth/login', form, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
      await prepareCsrf()
      this.profile = (await api.get<Profile>('/auth/me')).data
    },
    async logout() {
      await api.post('/auth/logout')
      this.profile = null
    }
  }
})
