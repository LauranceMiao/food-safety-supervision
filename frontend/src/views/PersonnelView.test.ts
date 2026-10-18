// @vitest-environment vue-custom-renderer
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, defineComponent, h, nextTick } from 'vue'
import PersonnelView from './PersonnelView.vue'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), warning: vi.fn(), success: vi.fn(),
  auth: { isWorker: true, isManager: false, isAdmin: false, isInspector: false, isPrincipal: false,
    isChef: true, isPurchaser: false, isCleaner: false, isStaff: false,
    profile: { id: 31, username: 'chushi', displayName: '王厨师', canteenIds: [1], roles: ['CHEF'] } } }))
vi.mock('../api', () => ({ api: { get: mocks.get, post: mocks.post, put: mocks.put } }))
vi.mock('../store', () => ({ useAuth: () => mocks.auth }))
vi.mock('element-plus', () => ({ ElMessage: { warning: mocks.warning, success: mocks.success } }))

type Row = Record<string, any>
type HostNode = { type: string; text: string; props: Record<string, unknown>; children: HostNode[]; parent: HostNode | null }
const node = (type: string, text = ''): HostNode => ({ type, text, props: {}, children: [], parent: null })
const renderer = createRenderer<HostNode, HostNode>({
  createElement: type => node(type), createText: text => node('text', text), createComment: text => node('comment', text),
  setText: (el, text) => { el.text = text }, setElementText: (el, text) => { el.text = text; el.children = [] },
  patchProp: (el, key, _previous, value) => { el.props[key] = value },
  insert: (el, parent, anchor = null) => {
    if (el.parent) el.parent.children.splice(el.parent.children.indexOf(el), 1)
    el.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    if (index < 0) parent.children.push(el)
    else parent.children.splice(index, 0, el)
  },
  remove: el => { if (el.parent) el.parent.children.splice(el.parent.children.indexOf(el), 1); el.parent = null },
  parentNode: el => el.parent, nextSibling: el => el.parent?.children[el.parent.children.indexOf(el) + 1] ?? null
})
const DialogStub = defineComponent({ props: { modelValue: Boolean, title: String },
  setup(props, { slots, attrs }) { return () => props.modelValue ? h('dialog', { ...attrs, title: props.title }, [slots.default?.(), slots.footer?.()]) : null } })
const ContainerStub = defineComponent({ setup(_props, { slots, attrs }) { return () => h('container', attrs, slots.default?.()) } })
const ButtonStub = defineComponent({ setup(_props, { slots, attrs }) { return () => h('button', attrs, slots.default?.()) } })
const TableStub = defineComponent({ props: { data: Array }, setup(props) { return () => h('table', { data: props.data }) } })
const unmounts: (() => void)[] = []
const textContent = (root: HostNode): string => root.text + root.children.map(textContent).join('')
const descendants = (root: HostNode): HostNode[] => [root, ...root.children.flatMap(descendants)]
async function settle() { for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() } }
type State = {
  rows: Row[]; submissions: Row[]; reviews: Row[]; visibleRows: Row[]; visibleReviews: Row[]; history: Row[]
  form: Record<string, any>; editDialog: boolean; editingId: number | null; editingVersion: number; saving: boolean
  formError: string; sourceReviewNote: string; detailDialog: boolean; detail: Row | null
  reviewDialog: boolean; reviewTarget: Row | null; reviewing: boolean; reviewError: string
  reviewForm: { decision: string; note: string }; selectedCanteen: number; nameSearch: string; positionSearch: string
  openEditor(row?: Row): void; saveDraft(): Promise<void>; submitForReview(): Promise<void>
  openReview(row: Row): void; reviewSubmission(): Promise<void>; openDetail(row: Row): void
}
async function mountPersonnel() {
  const app = renderer.createApp(PersonnelView)
  app.component('el-dialog', DialogStub); app.component('el-button', ButtonStub); app.component('el-table', TableStub)
  for (const name of ['el-select', 'el-option', 'el-input', 'el-alert', 'el-tag', 'el-form', 'el-form-item', 'el-date-picker',
    'el-descriptions', 'el-descriptions-item', 'el-tabs', 'el-tab-pane', 'el-radio-group', 'el-radio']) app.component(name, ContainerStub)
  const root = node('root')
  const instance = app.mount(root)
  unmounts.push(() => app.unmount()); await settle()
  return { state: (instance.$ as unknown as { setupState: State }).setupState, root }
}
const submission = { id: 12, version: 4, user_id: 31, canteen_id: 1, real_name: '王小明', display_name: '王厨师',
  canteen_name: '第一食堂', position_name: '厨师', health_cert_no: '健康证001', health_expires_on: '2027-09-26',
  certificate_path: '/api/files/my-health.pdf', last_training_on: '2026-09-25', status: 'DRAFT' }
let approved: Row[] = []
let own: Row[] = []
let reviewQueue: Row[] = []
function fromForm(body: Row, id = 90, version = 1): Row {
  return { id, version, status: 'DRAFT', user_id: 31, canteen_id: body.canteenId, real_name: body.realName,
    position_name: body.positionName, health_cert_no: body.healthCertNo, health_expires_on: body.healthExpiresOn,
    certificate_path: body.certificatePath, canteen_name: '第一食堂' }
}
beforeEach(() => {
  vi.clearAllMocks(); approved = []; own = []; reviewQueue = []
  Object.assign(mocks.auth, { isWorker: true, isManager: false, isAdmin: false, isInspector: false, isPrincipal: false,
    isChef: true, isPurchaser: false, isCleaner: false, isStaff: false })
  mocks.auth.profile = { id: 31, username: 'chushi', displayName: '王厨师', canteenIds: [1], roles: ['CHEF'] }
  mocks.get.mockImplementation(async (path: string) => {
    const responses: Record<string, any> = { '/personnel': approved, '/canteens': [{ id: 1, name: '第一食堂' }, { id: 2, name: '第二食堂' }], '/personnel/my': own, '/personnel/reviews': reviewQueue }
    if (!(path in responses)) throw new Error(`Unexpected GET ${path}`)
    return { data: responses[path] }
  })
  mocks.post.mockImplementation(async (path: string, body: Row) => {
    if (path === '/personnel/my/drafts') return { data: fromForm(body) }
    throw new Error(`Unexpected POST ${path}`)
  })
  mocks.put.mockImplementation(async (_path: string, body: Row) => ({ data: fromForm(body, 12, Number(body.version) + 1) }))
})
afterEach(() => unmounts.splice(0).forEach(unmount => unmount()))

describe('worker health self entry and manager review', () => {
  it.each(['CHEF', 'PURCHASER', 'CLEANER', 'STAFF'])('lets a new %s worker create their own incomplete draft without an existing profile', async role => {
    Object.assign(mocks.auth, { isChef: role === 'CHEF', isPurchaser: role === 'PURCHASER', isCleaner: role === 'CLEANER', isStaff: role === 'STAFF' })
    mocks.auth.profile.roles = [role]
    const { state, root } = await mountPersonnel()
    expect(textContent(root)).toContain('填写我的健康档案')
    expect(mocks.get.mock.calls.map(args => args[0])).toEqual(['/personnel', '/canteens', '/personnel/my'])
    state.openEditor(); await settle()
    expect(state.editDialog).toBe(true); expect(state.form.canteenId).toBe(1)
    state.form.realName = ''; state.form.healthCertNo = ''
    await state.saveDraft()
    expect(mocks.post).toHaveBeenCalledWith('/personnel/my/drafts', expect.objectContaining({ canteenId: 1, realName: '', healthCertNo: '' }))
    expect(mocks.post.mock.calls[0]?.[1]).not.toHaveProperty('userId')
    expect(state.editingId).toBe(90); expect(state.editingVersion).toBe(1)
    expect(state.editDialog).toBe(true)
  })

  it('persists edited inputs and submits the latest version while retaining an older approved snapshot', async () => {
    own = [{ ...submission }]
    const previous = { ...submission, approved_submission_id: 5, real_name: '上一版姓名' }
    approved = [previous]
    mocks.post.mockImplementation(async (path: string, body: Row) => {
      if (path === '/personnel/my/submissions/12/submit') {
        own = [{ ...submission, status: 'PENDING', version: 6 }]
        expect(body.version).toBe(5)
        return { data: own[0] }
      }
      throw new Error(`Unexpected POST ${path}`)
    })
    const { state } = await mountPersonnel()
    state.openEditor(); state.form.realName = '本人更新的姓名'
    await state.submitForReview()
    expect(mocks.put).toHaveBeenCalledWith('/personnel/my/submissions/12', expect.objectContaining({ version: 4, realName: '本人更新的姓名' }))
    expect(mocks.post).toHaveBeenCalledWith('/personnel/my/submissions/12/submit', { version: 5 })
    expect(state.editDialog).toBe(false); expect(state.submissions[0]?.status).toBe('PENDING')
    expect(state.rows).toEqual([previous])
  })

  it('keeps a pending submission read only and rejects an attempt to edit it', async () => {
    own = [{ ...submission, status: 'PENDING' }]
    const { state } = await mountPersonnel()
    state.openEditor(own[0]); await settle()
    expect(state.editDialog).toBe(false); expect(state.detailDialog).toBe(true)
    expect(state.detail?.status).toBe('PENDING'); expect(mocks.warning).toHaveBeenCalled()
    await state.saveDraft(); await state.submitForReview()
    expect(mocks.post).not.toHaveBeenCalled(); expect(mocks.put).not.toHaveBeenCalled()
  })

  it('preserves rejection history and creates a separate draft when the employee revises it', async () => {
    own = [{ ...submission, status: 'REJECTED', review_note: '健康证明照片不清晰，请重新上传' }]
    const { state, root } = await mountPersonnel()
    state.openEditor(own[0]); await settle()
    expect(state.editingId).toBeNull(); expect(state.sourceReviewNote).toContain('照片不清晰')
    expect(textContent(root)).toContain('原证明由总管代上传')
    state.form.certificatePath = '/api/files/new-health.pdf'
    await state.saveDraft()
    expect(mocks.post).toHaveBeenCalledWith('/personnel/my/drafts', expect.objectContaining({ certificatePath: '/api/files/new-health.pdf' }))
    expect(mocks.put).not.toHaveBeenCalled()
    expect(state.submissions.find(row => row.id === 12)).toMatchObject({ status: 'REJECTED', review_note: '健康证明照片不清晰，请重新上传' })
  })

  it('requires the certificate before submitting and retains entries if submission fails', async () => {
    const { state } = await mountPersonnel()
    state.openEditor(); state.form.realName = '本人姓名'; state.form.healthCertNo = '001'; state.form.healthExpiresOn = '2027-09-26'
    await state.submitForReview()
    expect(mocks.post).not.toHaveBeenCalled(); expect(state.formError).toContain('上传本人健康证明')
    state.form.certificatePath = '/api/files/new-health.pdf'
    mocks.post.mockImplementation(async (path: string, body: Row) => {
      if (path === '/personnel/my/drafts') return { data: fromForm(body) }
      throw { response: { data: { error: '档案已被修改，请刷新后重试' } } }
    })
    await state.submitForReview(); await settle()
    expect(state.editDialog).toBe(true); expect(state.form.realName).toBe('本人姓名')
    expect(state.form.certificatePath).toBe('/api/files/new-health.pdf'); expect(state.editingId).toBe(90)
    expect(state.formError).toBe('档案已被修改，请刷新后重试'); expect(state.saving).toBe(false)
  })

  it('prevents duplicate save requests while the first save is pending', async () => {
    const { state, root } = await mountPersonnel(); state.openEditor()
    let resolve!: (value: { data: Row }) => void
    mocks.post.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const first = state.saveDraft(); await state.saveDraft(); await state.submitForReview(); await settle()
    expect(mocks.post).toHaveBeenCalledOnce(); expect(state.saving).toBe(true)
    expect(descendants(root).find(row => row.type === 'button' && textContent(row) === '取消')?.props.disabled).toBe(true)
    resolve({ data: fromForm(state.form) }); await first
    expect(state.saving).toBe(false)
  })

  it('lets a manager who is also a worker fill their own information and load both areas, but never self review', async () => {
    mocks.auth.isManager = true
    mocks.auth.profile.roles = ['MANAGER', 'CHEF']
    reviewQueue = [{ ...submission, status: 'PENDING' }]
    const { state, root } = await mountPersonnel()
    expect(mocks.get.mock.calls.map(args => args[0])).toEqual(['/personnel', '/canteens', '/personnel/my', '/personnel/reviews'])
    expect(textContent(root)).toContain('填写我的健康档案')
    expect(textContent(root)).toContain('健康档案审核')
    state.openEditor(); expect(state.editDialog).toBe(true)
    state.openReview(reviewQueue[0]!)
    expect(state.reviewDialog).toBe(false)
    expect(mocks.warning).toHaveBeenCalledWith('本人提交的健康档案需由其他负责该食堂的总管审核')
  })

  it('clears an old canteen attachment when a new draft changes canteen', async () => {
    mocks.auth.profile.canteenIds = [1, 2]
    const { state } = await mountPersonnel(); state.openEditor()
    state.form.certificatePath = '/api/files/canteen-1.pdf'
    state.form.canteenId = 2
    expect(state.form.certificatePath).toBe('')
    expect(mocks.warning).toHaveBeenCalledWith('已切换食堂，请为当前食堂重新上传本人健康证明')
    state.form.certificatePath = '/api/files/canteen-2.pdf'
    await state.saveDraft()
    expect(mocks.post).toHaveBeenCalledWith('/personnel/my/drafts', expect.objectContaining({ canteenId: 2, certificatePath: '/api/files/canteen-2.pdf' }))
  })

  it('lets the manager review a submitted record, requires a rejection reason and refreshes approved data', async () => {
    Object.assign(mocks.auth, { isWorker: false, isChef: false, isManager: true })
    reviewQueue = [{ ...submission, user_id: 40, status: 'PENDING' }]
    const { state, root } = await mountPersonnel()
    expect(mocks.get.mock.calls.map(args => args[0])).toEqual(['/personnel', '/canteens', '/personnel/reviews'])
    expect(textContent(root)).not.toContain('填写我的健康档案')
    state.openReview(reviewQueue[0]!); state.reviewForm.decision = 'REJECTED'; state.reviewForm.note = '   '
    await state.reviewSubmission()
    expect(mocks.post).not.toHaveBeenCalled(); expect(state.reviewError).toContain('必须填写具体理由')
    state.reviewForm.decision = 'APPROVED'; state.reviewForm.note = '已核对证明'
    mocks.post.mockImplementationOnce(async () => {
      approved = [{ ...submission, approved_submission_id: 12, approved_at: '2026-09-26T18:00:00' }]
      reviewQueue = [{ ...submission, status: 'APPROVED' }]
      return { data: reviewQueue[0] }
    })
    // A separate detail dialog must never clear the review target.
    state.openDetail(submission); state.detail = null; state.detailDialog = false
    await state.reviewSubmission()
    expect(mocks.post).toHaveBeenCalledWith('/personnel/submissions/12/review', { decision: 'APPROVED', note: '已核对证明', version: 4 })
    expect(state.reviewDialog).toBe(false); expect(state.visibleReviews).toEqual([])
    expect(state.rows[0]?.approved_submission_id).toBe(12)
  })

  it('keeps the manager rejection reason after a failed review request', async () => {
    Object.assign(mocks.auth, { isWorker: false, isChef: false, isManager: true })
    reviewQueue = [{ ...submission, user_id: 40, status: 'PENDING' }]
    const { state } = await mountPersonnel()
    state.openReview(reviewQueue[0]!); state.reviewForm.decision = 'REJECTED'; state.reviewForm.note = '请上传清晰照片'
    mocks.post.mockRejectedValueOnce({ response: { data: { error: '审核版本已变化' } } })
    await state.reviewSubmission()
    expect(mocks.post).toHaveBeenCalledWith('/personnel/submissions/12/review', { decision: 'REJECTED', note: '请上传清晰照片', version: 4 })
    expect(state.reviewDialog).toBe(true); expect(state.reviewForm.note).toBe('请上传清晰照片')
    expect(state.reviewError).toBe('审核版本已变化'); expect(state.reviewing).toBe(false)
  })

  it.each(['isInspector', 'isPrincipal', 'isAdmin'] as const)('shows approved information only to %s without requesting employee or manager records', async role => {
    Object.assign(mocks.auth, { isWorker: false, isChef: false, [role]: true })
    approved = [{ ...submission, approved_submission_id: 12 }, { ...submission, user_id: 40, canteen_id: 2, real_name: '李保洁', position_name: '保洁员' }]
    const { state, root } = await mountPersonnel()
    expect(mocks.get.mock.calls.map(args => args[0])).toEqual(['/personnel', '/canteens'])
    expect(textContent(root)).not.toContain('填写我的健康档案'); expect(textContent(root)).not.toContain('健康档案审核')
    expect(state.visibleRows).toHaveLength(2)
    state.selectedCanteen = 2; state.nameSearch = '李'; state.positionSearch = '保洁'
    expect(state.visibleRows).toHaveLength(1)
    state.openEditor(); state.openReview({ ...submission, status: 'PENDING' })
    expect(state.editDialog).toBe(false); expect(state.reviewDialog).toBe(false)
  })
})
