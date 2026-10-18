// @vitest-environment vue-custom-renderer
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, defineComponent, h, nextTick, watch } from 'vue'
import InspectionView from './InspectionView.vue'

const mocks = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), warning: vi.fn(), success: vi.fn(), prompt: vi.fn()
}))
vi.mock('../api', () => ({ api: { get: mocks.get, post: mocks.post }, toDateTime: String }))
vi.mock('../store', () => ({ useAuth: () => ({ isInspector: true, profile: { id: 31 } }) }))
vi.mock('element-plus', () => ({
  ElMessage: { warning: mocks.warning, success: mocks.success },
  ElMessageBox: { prompt: mocks.prompt }
}))

type HostNode = {
  type: string; text: string; props: Record<string, unknown>; children: HostNode[]; parent: HostNode | null
}
const node = (type: string, text = ''): HostNode => ({ type, text, props: {}, children: [], parent: null })
const renderer = createRenderer<HostNode, HostNode>({
  createElement: type => node(type), createText: text => node('text', text), createComment: text => node('comment', text),
  setText: (el, text) => { el.text = text },
  setElementText: (el, text) => { el.text = text; el.children = [] },
  patchProp: (el, key, _previous, value) => { el.props[key] = value },
  insert: (el, parent, anchor = null) => {
    if (el.parent) el.parent.children.splice(el.parent.children.indexOf(el), 1)
    el.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    if (index < 0) parent.children.push(el)
    else parent.children.splice(index, 0, el)
  },
  remove: el => {
    if (el.parent) el.parent.children.splice(el.parent.children.indexOf(el), 1)
    el.parent = null
  },
  parentNode: el => el.parent,
  nextSibling: el => el.parent?.children[(el.parent.children.indexOf(el)) + 1] ?? null
})

type Result = { itemId: number; title: string; result: string; note: string }
type InspectionState = {
  detail: Record<string, unknown> | null; detailDialog: boolean; completeDialog: boolean
  completionTaskId: number | null; submitting: boolean; submissionError: string; rows: Record<string, unknown>[]
  completeForm: { note: string; results: Result[] }
  show(id: number): Promise<void>
  openComplete(row: { id: number }): Promise<void>
  complete(): Promise<void>
}
const task = { id: 42, inspector_id: 31, template_id: 7, status: 'IN_PROGRESS', results: [] }
const completedTask = { ...task, status: 'COMPLETED', note: '无问题', results: [{ item_title: '餐具清洗', result: 'PASS' }] }
const openedDialogs: string[] = []
let onDialogClosed: (title: string) => void = () => undefined
const DialogStub = defineComponent({
  props: {
    modelValue: Boolean, title: { type: String, default: '' },
    closeOnClickModal: Boolean, closeOnPressEscape: Boolean, showClose: Boolean
  },
  emits: ['update:modelValue', 'close', 'closed'],
  setup(props, { slots, emit }) {
    watch(() => props.modelValue, async (open, previous) => {
      if (open) openedDialogs.push(props.title)
      if (previous && !open) {
        // Element Plus dispatches close/closed while its closing transition finishes.
        await nextTick()
        emit('close')
        emit('closed')
        onDialogClosed(props.title)
      }
    }, { immediate: true })
    return () => props.modelValue ? h('dialog', { ...props }, [slots.default?.(), slots.footer?.()]) : null
  }
})
const ContainerStub = defineComponent({
  setup(_props, { slots, attrs }) { return () => h('container', attrs, slots.default?.()) }
})
const ButtonStub = defineComponent({
  setup(_props, { slots, attrs }) { return () => h('button', attrs, slots.default?.()) }
})
const TableStub = defineComponent({ setup: () => () => h('table') })
const unmounts: (() => void)[] = []
async function settle() {
  for (let index = 0; index < 4; index++) { await Promise.resolve(); await nextTick() }
}
async function mountInspection() {
  const app = renderer.createApp(InspectionView)
  app.component('el-dialog', DialogStub)
  app.component('el-button', ButtonStub)
  app.component('el-table', TableStub)
  for (const name of ['el-table-column', 'el-form', 'el-form-item', 'el-select', 'el-option', 'el-date-picker', 'el-input', 'el-alert', 'el-radio-group', 'el-radio']) {
    app.component(name, ContainerStub)
  }
  const root = node('root')
  const instance = app.mount(root)
  unmounts.push(() => app.unmount())
  await settle()
  return { state: (instance.$ as unknown as { setupState: InspectionState }).setupState, root }
}
const descendants = (root: HostNode): HostNode[] => [root, ...root.children.flatMap(descendants)]
const textContent = (root: HostNode): string => root.text + root.children.map(textContent).join('')

beforeEach(() => {
  vi.clearAllMocks()
  openedDialogs.length = 0
  onDialogClosed = () => undefined
  mocks.get.mockImplementation(async (path: string) => {
    const responses: Record<string, unknown> = {
      '/inspections': [task], '/canteens': [], '/check-templates': [{ id: 7, active: true }], '/inspectors': [],
      '/inspections/42': { ...task },
      '/check-templates/7/items': [{ id: 9, title: '餐具清洗', active: true }, { id: 10, title: '停用项目', active: false }]
    }
    if (!(path in responses)) throw new Error(`Unexpected GET ${path}`)
    return { data: responses[path] }
  })
  mocks.post.mockResolvedValue({ data: completedTask })
})
afterEach(() => { unmounts.splice(0).forEach(unmount => unmount()) })

describe('inspection completion form', () => {
  it('opens the result form directly without opening the detail dialog', async () => {
    const { state } = await mountInspection()
    await state.openComplete(task)
    await settle()

    expect(openedDialogs).toEqual(['填写现场检查结果'])
    expect(state.detailDialog).toBe(false)
    expect(state.detail).toBeNull()
    expect(state.completeDialog).toBe(true)
    expect(state.completionTaskId).toBe(42)
    expect(state.completeForm.results).toEqual([{ itemId: 9, title: '餐具清洗', result: 'PASS', note: '' }])
  })

  it('submits the correct task even if the previous detail closes asynchronously and is cleared', async () => {
    const { state } = await mountInspection()
    await state.show(42)
    await settle()
    onDialogClosed = title => { if (title === '检查任务详情') state.detail = null }
    await state.openComplete(task)
    await settle()
    expect(state.detail).toBeNull()
    state.completeForm.note = '无问题'
    mocks.get.mockImplementationOnce(async () => ({ data: [completedTask] }))

    await state.complete()
    await settle()

    expect(mocks.post).toHaveBeenCalledWith('/inspections/42/complete', {
      note: '无问题', results: [{ itemId: 9, title: '餐具清洗', result: 'PASS', note: '' }]
    })
    expect(state.rows[0]?.status).toBe('COMPLETED')
    expect(state.completeDialog).toBe(false)
    expect(state.detailDialog).toBe(true)
    expect(state.detail?.status).toBe('COMPLETED')
    expect(state.completionTaskId).toBeNull()
    expect(mocks.success).toHaveBeenCalledOnce()
  })

  it('requires a nonblank explanation for an abnormal item before posting', async () => {
    const { state } = await mountInspection()
    await state.openComplete(task)
    state.completeForm.results[0]!.result = 'FAIL'
    state.completeForm.results[0]!.note = '   '

    await state.complete()

    expect(mocks.post).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledWith('异常项目必须填写说明')
    expect(state.completeDialog).toBe(true)
    expect(state.submitting).toBe(false)
  })

  it('preserves the open form, item explanation and conclusion after an API failure', async () => {
    const { state } = await mountInspection()
    await state.openComplete(task)
    state.completeForm.results[0]!.result = 'FAIL'
    state.completeForm.results[0]!.note = '餐具未清洁'
    state.completeForm.note = '需要整改'
    mocks.post.mockRejectedValueOnce({ response: { status: 400, data: { error: '检查项目已调整，请重新打开表单' } } })

    await state.complete()
    await settle()

    expect(state.completeDialog).toBe(true)
    expect(state.detailDialog).toBe(false)
    expect(state.completionTaskId).toBe(42)
    expect(state.completeForm.note).toBe('需要整改')
    expect(state.completeForm.results[0]).toMatchObject({ result: 'FAIL', note: '餐具未清洁' })
    expect(state.submitting).toBe(false)
    expect(state.submissionError).toBe('检查项目已调整，请重新打开表单')
    expect(mocks.success).not.toHaveBeenCalled()
  })

  it('posts once on a double click, shows loading, and releases the loading state after saving', async () => {
    const { state, root } = await mountInspection()
    await state.openComplete(task)
    let resolvePost!: (value: { data: typeof completedTask }) => void
    mocks.post.mockImplementationOnce(() => new Promise(resolve => { resolvePost = resolve }))
    const saving = state.complete()
    await state.complete()
    await settle()

    expect(mocks.post).toHaveBeenCalledOnce()
    expect(state.submitting).toBe(true)
    const buttons = descendants(root).filter(element => element.type === 'button')
    expect(buttons.find(button => textContent(button) === '正在保存…')?.props.loading).toBe(true)
    expect(buttons.find(button => textContent(button) === '取消')?.props.disabled).toBe(true)
    const dialog = descendants(root).find(element => element.type === 'dialog')
    expect(dialog?.props.closeOnPressEscape).toBe(false)
    expect(dialog?.props.showClose).toBe(false)

    resolvePost({ data: completedTask })
    await saving
    await settle()

    expect(state.submitting).toBe(false)
    expect(state.completeDialog).toBe(false)
    expect(state.detailDialog).toBe(true)
    expect(mocks.post).toHaveBeenCalledOnce()
  })
})
