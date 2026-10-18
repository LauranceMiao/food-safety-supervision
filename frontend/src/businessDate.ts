const schoolTimeZone = 'Asia/Shanghai'
const schoolDateFormatter = new Intl.DateTimeFormat('sv-SE', {
  timeZone: schoolTimeZone, year: 'numeric', month: '2-digit', day: '2-digit'
})

/** Date-only database values stay date-only; instants use the school's calendar day. */
export function businessDate(value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  const text = String(value)
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return text
  if (/^\d{4}-\d{2}-\d{2}[T ]/.test(text) && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)) return text.slice(0, 10)
  const date = value instanceof Date ? value : new Date(typeof value === 'number' ? value : text)
  return Number.isNaN(date.getTime()) ? '' : schoolDateFormatter.format(date)
}

export function todayInSchool(): string { return businessDate(new Date()) }

export function dailyMenuRows<T extends Record<string, any>>(rows: T[], canteenId: number, date: string): T[] {
  const meals = ['BREAKFAST', 'LUNCH', 'DINNER']
  return rows.filter(row => Number(row.canteen_id) === canteenId
    && businessDate(row.service_date) === date && row.status !== 'CANCELLED')
    .sort((a, b) => meals.indexOf(String(a.meal)) - meals.indexOf(String(b.meal)) || Number(a.id) - Number(b.id))
}
