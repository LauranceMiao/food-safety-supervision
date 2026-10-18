import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from './store'
import { canOpenPath } from './roleAccess'

const routes = [
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('./views/RegisterView.vue'), meta: { public: true } },
  { path: '/', component: () => import('./views/DashboardView.vue') },
  { path: '/suppliers', component: () => import('./views/SupplierView.vue') },
  { path: '/batches', component: () => import('./views/BatchView.vue') },
  { path: '/menus', component: () => import('./views/MenuView.vue') },
  { path: '/daily-menu', component: () => import('./views/DailyMenuView.vue') },
  { path: '/daily', component: () => import('./views/DailyView.vue') },
  { path: '/inspections', component: () => import('./views/InspectionView.vue') },
  { path: '/issues', component: () => import('./views/IssueView.vue') },
  { path: '/feedback', component: () => import('./views/FeedbackView.vue') },
  { path: '/algorithms', component: () => import('./views/AlgorithmView.vue') },
  { path: '/reports', component: () => import('./views/ReportsView.vue') },
  { path: '/personnel', component: () => import('./views/PersonnelView.vue') },
  { path: '/operations', component: () => import('./views/OperationView.vue') },
  { path: '/plans', component: () => import('./views/ProcurementPlanView.vue') },
  { path: '/reviews', component: () => import('./views/ReviewView.vue') },
  { path: '/admin', component: () => import('./views/AdminView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(async to => {
  const auth = useAuth()
  if (!auth.profile && !auth.loading) await auth.load()
  if (!to.meta.public && !auth.profile) return '/login'
  if (auth.profile && (to.path === '/login' || to.path === '/register')) return '/'
  if (!to.meta.public && !canOpenPath(auth.profile, to.path)) return '/'
  return true
})
