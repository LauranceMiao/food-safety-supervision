import type { Profile } from './store'

export type Role = 'ADMIN' | 'PRINCIPAL' | 'MANAGER' | 'INSPECTOR' | 'PURCHASER' | 'CHEF' | 'CLEANER' | 'STAFF' | 'COMMUNITY'
export type NavItem = { path: string; label: string }
export type NavGroup = { title: string; items: NavItem[] }

const order: Role[] = ['ADMIN', 'PRINCIPAL', 'MANAGER', 'INSPECTOR', 'PURCHASER', 'CHEF', 'CLEANER', 'STAFF', 'COMMUNITY']
export const roleLabels: Record<Role, string> = {
  ADMIN: '超级管理员', PRINCIPAL: '校长', MANAGER: '食堂总管',
  INSPECTOR: '监管人员', PURCHASER: '采购员', CHEF: '厨师', CLEANER: '保洁员', STAFF: '其他工作人员', COMMUNITY: '师生'
}

const myTasks: NavGroup = { title: '我的待办与档案', items: [{ path: '/issues', label: '我的整改任务' }, { path: '/feedback', label: '我的调查任务' }, { path: '/personnel', label: '我的健康档案' }] }

const groups: Record<Role, NavGroup[]> = {
  ADMIN: [
    { title: '系统治理', items: [{ path: '/', label: '系统工作台' }, { path: '/admin', label: '账号与基础配置' }] },
    { title: '全校核查', items: [{ path: '/reports', label: '统计报表' }, { path: '/issues', label: '问题记录' }] }
  ],
  PRINCIPAL: [
    { title: '校级决策', items: [{ path: '/', label: '校级驾驶舱' }, { path: '/reports', label: '全校统计' }, { path: '/algorithms', label: '食堂风险' }] },
    { title: '督办与调度', items: [{ path: '/issues', label: '问题督办' }, { path: '/reviews', label: '月度调度' }, { path: '/inspections', label: '检查进展' }] },
    { title: '资料抽查', items: [{ path: '/personnel', label: '人员健康' }, { path: '/suppliers', label: '供应商证照' }, { path: '/batches', label: '食材批次' }] }
  ],
  MANAGER: [
    { title: '食堂计划与管理', items: [{ path: '/', label: '运营工作台' }, { path: '/daily-menu', label: '每日食谱编排' }, { path: '/menus', label: '菜品与原料管理' }, { path: '/suppliers', label: '供应商与食材' }, { path: '/plans', label: '采购需求发布' }] },
    { title: '执行监督', items: [{ path: '/batches', label: '采购与验收记录' }, { path: '/operations', label: '留样与卫生记录' }, { path: '/personnel', label: '人员健康与审核' }, { path: '/daily', label: '每日管控检查' }] },
    { title: '整改与复盘', items: [{ path: '/issues', label: '食堂整改' }, { path: '/feedback', label: '师生反馈受理' }, { path: '/inspections', label: '检查记录' }, { path: '/reviews', label: '周排查' }, { path: '/reports', label: '本食堂报表' }] }
  ],
  INSPECTOR: [
    { title: '监管执行', items: [{ path: '/', label: '监管工作台' }, { path: '/inspections', label: '检查任务' }, { path: '/issues', label: '整改复核' }, { path: '/feedback', label: '反馈监督查看' }, { path: '/algorithms', label: '风险与排程' }] },
    { title: '证据与分析', items: [{ path: '/batches', label: '批次追溯' }, { path: '/suppliers', label: '供应商证照' }, { path: '/personnel', label: '人员健康' }, { path: '/reports', label: '监管统计' }] }
  ],
  STAFF: [
    { title: '我的工作', items: [{ path: '/', label: '我的工作台' }] }, myTasks
  ],
  PURCHASER: [
    { title: '采购与验收', items: [{ path: '/', label: '采购工作台' }, { path: '/plans', label: '已发布采购需求' }, { path: '/batches', label: '采购登记与验收' }, { path: '/suppliers', label: '供应商与食材查询' }] }, myTasks
  ],
  CHEF: [
    { title: '厨房制作', items: [{ path: '/', label: '厨房工作台' }, { path: '/daily-menu', label: '今日食谱与制作' }, { path: '/menus', label: '实际用料与供餐' }, { path: '/operations', label: '食品留样' }] }, myTasks
  ],
  CLEANER: [
    { title: '环境卫生', items: [{ path: '/', label: '保洁工作台' }, { path: '/operations', label: '清洁与消毒' }] }, myTasks
  ],
  COMMUNITY: [{ title: '师生服务', items: [{ path: '/', label: '反馈首页' }, { path: '/feedback', label: '我的反馈' }] }]
}

export function primaryRole(profile: Profile | null): Role | null {
  return order.find(role => profile?.roles.includes(role)) ?? null
}

export function roleLabel(profile: Profile | null): string {
  const role = primaryRole(profile)
  return role ? roleLabels[role] : '未授权账号'
}

export function navigationFor(profile: Profile | null): NavGroup[] {
  const role = primaryRole(profile)
  if (!role || !profile) return []
  const primary = groups[role]
  const seen = new Set(primary.flatMap(group => group.items.map(item => item.path)))
  const extra = order.filter(other => other !== role && profile.roles.includes(other))
    .flatMap(other => groups[other].flatMap(group => group.items))
    .filter(item => { if (seen.has(item.path)) return false; seen.add(item.path); return true })
  return extra.length ? [...primary, { title: '其他授权', items: extra }] : primary
}

export function canOpenPath(profile: Profile | null, path: string): boolean {
  if (!profile) return false
  return navigationFor(profile).some(group => group.items.some(item => item.path === path))
}
