import { describe, expect, it } from 'vitest'
import { businessDate, dailyMenuRows } from './businessDate'

describe('school calendar dates', () => {
  it('keeps SQL dates and local date-times while converting zoned instants to Shanghai', () => {
    expect(businessDate('2026-09-26')).toBe('2026-09-26')
    expect(businessDate('2026-09-26T00:00:00')).toBe('2026-09-26')
    expect(businessDate('2026-09-25T16:00:00.000+00:00')).toBe('2026-09-26')
    expect(businessDate('2026-09-25T16:00:00.000Z')).toBe('2026-09-26')
    expect(businessDate('2026-09-26T00:00:00+08:00')).toBe('2026-09-26')
    expect(businessDate(null)).toBe('')
  })

  it('shows saved drafts on the selected school day and does not mix other canteens or cancelled entries', () => {
    const rows = [
      { id: 2, canteen_id: 1, service_date: '2026-09-25T16:00:00.000+00:00', meal: 'DINNER', status: 'PLANNED', published_at: null },
      { id: 1, canteen_id: 1, service_date: '2026-09-25T16:00:00.000+00:00', meal: 'LUNCH', status: 'PLANNED', published_at: null },
      { id: 3, canteen_id: 2, service_date: '2026-09-26', meal: 'LUNCH', status: 'PLANNED' },
      { id: 4, canteen_id: 1, service_date: '2026-09-26', meal: 'LUNCH', status: 'CANCELLED' },
      { id: 5, canteen_id: 1, service_date: '2026-09-27', meal: 'LUNCH', status: 'PLANNED' }
    ]
    expect(dailyMenuRows(rows, 1, '2026-09-26').map(row => row.id)).toEqual([1, 2])
  })
})
