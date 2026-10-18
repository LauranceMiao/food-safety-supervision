import { describe, expect, it } from 'vitest'
import { canOpenPath, navigationFor, primaryRole } from './roleAccess'
import type { Profile } from './store'

const account = (...roles: string[]): Profile => ({ id: 1, username: 'test', displayName: '测试', roles, canteenIds: [1] })

describe('role-specific navigation', () => {
  it('gives each role a distinct first task and limits direct page access', () => {
    const cases = [
      { role: 'ADMIN', first: '系统工作台', yes: '/admin', no: '/plans' },
      { role: 'PRINCIPAL', first: '校级驾驶舱', yes: '/reviews', no: '/feedback' },
      { role: 'MANAGER', first: '运营工作台', yes: '/plans', no: '/admin' },
      { role: 'INSPECTOR', first: '监管工作台', yes: '/algorithms', no: '/plans' },
      { role: 'PURCHASER', first: '采购工作台', yes: '/batches', no: '/daily-menu' },
      { role: 'CHEF', first: '厨房工作台', yes: '/daily-menu', no: '/plans' },
      { role: 'CLEANER', first: '保洁工作台', yes: '/operations', no: '/menus' },
      { role: 'STAFF', first: '我的工作台', yes: '/issues', no: '/operations' },
      { role: 'COMMUNITY', first: '反馈首页', yes: '/feedback', no: '/issues' }
    ]
    for (const { role, first, yes, no } of cases) {
      const profile = account(role)
      expect(navigationFor(profile)[0]?.items[0]?.label).toBe(first)
      expect(canOpenPath(profile, yes)).toBe(true)
      expect(canOpenPath(profile, no)).toBe(false)
    }
  })

  it('keeps additional authorizations for a user with two roles', () => {
    const profile = account('CHEF', 'INSPECTOR')
    expect(primaryRole(profile)).toBe('INSPECTOR')
    expect(canOpenPath(profile, '/inspections')).toBe(true)
    expect(canOpenPath(profile, '/daily-menu')).toBe(true)
    expect(navigationFor(profile).some(group => group.title === '其他授权')).toBe(true)
  })

  it('limits the daily menu to the manager and chef', () => {
    expect(canOpenPath(account('MANAGER'), '/daily-menu')).toBe(true)
    expect(canOpenPath(account('CHEF'), '/daily-menu')).toBe(true)
    expect(canOpenPath(account('PURCHASER'), '/daily-menu')).toBe(false)
    expect(canOpenPath(account('CLEANER'), '/daily-menu')).toBe(false)
    expect(canOpenPath(account('STAFF'), '/daily-menu')).toBe(false)
    expect(canOpenPath(account('COMMUNITY'), '/daily-menu')).toBe(false)
    expect(canOpenPath(account('INSPECTOR'), '/daily-menu')).toBe(false)
  })

  it('separates procurement, kitchen and cleaning responsibilities', () => {
    const purchaser = account('PURCHASER')
    const chef = account('CHEF')
    const cleaner = account('CLEANER')
    expect(canOpenPath(purchaser, '/plans')).toBe(true)
    expect(canOpenPath(purchaser, '/operations')).toBe(false)
    expect(canOpenPath(chef, '/menus')).toBe(true)
    expect(canOpenPath(chef, '/batches')).toBe(false)
    expect(canOpenPath(cleaner, '/operations')).toBe(true)
    expect(canOpenPath(cleaner, '/batches')).toBe(false)
    for (const profile of [purchaser, chef, cleaner]) {
      expect(canOpenPath(profile, '/personnel')).toBe(true)
      expect(canOpenPath(profile, '/daily')).toBe(false)
      expect(canOpenPath(profile, '/reports')).toBe(false)
      expect(canOpenPath(profile, '/admin')).toBe(false)
    }
  })
})
