import { describe, expect, it } from 'vitest'
import { batchNumber, display, displayAuditDetail } from './display'

describe('Chinese display values', () => {
  it('formats timestamps without changing date only values', () => {
    expect(display('2026-09-25T18:03:26')).toBe('2026-09-25 18:03')
    expect(display('2026-09-25')).toBe('2026-09-25')
    expect(display('2026-09-25T16:00:00.000Z')).toBe('2026-09-26 00:00')
  })

  it('shows readable status and numeric batch number', () => {
    expect(display('REVIEW')).toBe('待复核')
    expect(display('LUNCH')).toBe('午餐')
    expect(batchNumber({ id: 13, batch_code: 'BEEF-PLAN-cafc7629' })).toBe('13')
  })

  it('explains common audit details', () => {
    expect(displayAuditDetail('roles=[MANAGER],canteens=[1]')).toBe('角色=[食堂总管],食堂范围=[1]')
  })
})
