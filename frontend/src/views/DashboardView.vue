<script setup lang="ts">
import { display, batchNumber } from '../display'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { init, use, type ECharts } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { api } from '../api'
import { useAuth } from '../store'
import { primaryRole, roleLabels } from '../roleAccess'
import { businessDate, todayInSchool } from '../businessDate'

type Row = Record<string, any>
const auth = useAuth()
const router = useRouter()
const role = computed(() => primaryRole(auth.profile))
const loading = ref(false)
const users = ref<Row[]>([])
const canteens = ref<Row[]>([])
const audits = ref<Row[]>([])
const summary = ref<Row>({})
const risks = ref<Row[]>([])
const issues = ref<Row[]>([])
const inspections = ref<Row[]>([])
const batches = ref<Row[]>([])
const plans = ref<Row[]>([])
const feedback = ref<Row[]>([])
const operations = ref<Row[]>([])
const menus = ref<Row[]>([])
const personnel = ref<Row[]>([])
const healthSubmissions = ref<Row[]>([])
const healthReviews = ref<Row[]>([])
const reviews = ref<Row[]>([])
const chartEl = ref<HTMLElement>()
let chart: ECharts | undefined
use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const today = todayInSchool()
const openIssues = computed(() => issues.value.filter(row => row.status !== 'CLOSED'))
const dueIssues = computed(() => openIssues.value.filter(row => row.overdue || row.needs_report))
const pendingBatches = computed(() => batches.value.filter(row => row.status === 'PENDING'))
const newFeedback = computed(() => feedback.value.filter(row => row.status === 'NEW'))
const processingFeedback = computed(() => feedback.value.filter(row => row.status === 'PROCESSING'))
const reviewIssues = computed(() => issues.value.filter(row => row.status === 'REVIEW'))
const myInspections = computed(() => inspections.value.filter(row => Number(row.inspector_id) === auth.profile?.id && ['SCHEDULED', 'IN_PROGRESS'].includes(String(row.status))))
const todayOperations = computed(() => operations.value.filter(row => businessDate(row.operation_date) === today))
const publishedPlans = computed(() => plans.value.filter(row => row.published_at && row.status === 'OPEN'))
const todayMenus = computed(() => menus.value.filter(row => businessDate(row.service_date) === today && row.status !== 'CANCELLED'))
const pendingCooking = computed(() => todayMenus.value.filter(row => row.published_at && !row.prepared_at && row.status === 'PLANNED'))
const preparedMenus = computed(() => todayMenus.value.filter(row => row.prepared_at || row.status === 'SERVED'))
const isWorkerRole = computed(() => ['PURCHASER', 'CHEF', 'CLEANER', 'STAFF'].includes(role.value || ''))
const pendingHealthReviews = computed(() => healthReviews.value.filter(row => row.status === 'PENDING'))
const healthApplicationStatus = computed(() => {
  const latest = new Map<number, Row>()
  for (const row of healthSubmissions.value) {
    const canteen = Number(row.canteen_id)
    if (Number(row.id) > Number(latest.get(canteen)?.id || 0)) latest.set(canteen, row)
  }
  const statuses = [...latest.values()].map(row => row.status)
  if (statuses.includes('REJECTED')) return '已退回，请查看理由'
  if (statuses.includes('PENDING')) return '待总管审核'
  if (statuses.includes('DRAFT')) return '草稿待提交'
  if (statuses.length) return '已审核通过'
  return '尚未填写'
})
const healthStatus = computed(() => {
  const mine = personnel.value.filter(row => Number(row.user_id) === auth.profile?.id)
  if (!mine.length) return healthApplicationStatus.value
  if (mine.some(row => !row.health_expires_on)) return '待完善'
  if (mine.some(row => businessDate(row.health_expires_on) < today)) return '已过期'
  return '有效'
})

const presentation = computed(() => role.value ? ({
  ADMIN: ['系统运行与账号管理', '管理账号、食堂和检查模板，核对系统操作记录。'],
  PRINCIPAL: ['全校食堂安全态势', '查看全校趋势，督办重大与逾期问题，确认月度调度。'],
  MANAGER: ['食堂计划发布与执行监督', '安排食谱、发布食材采购需求，审核工作人员健康档案，监督采购、厨房和保洁执行，受理反馈并组织整改。'],
  INSPECTOR: ['检查、风险和复核待办', '按风险安排检查，监督食堂反馈处理并独立复核整改。'],
  PURCHASER: ['采购与验收工作台', '按总管发布的采购需求采购食材，登记到货并完成验收。'],
  CHEF: ['厨房制作工作台', '按已发布食谱制作菜品，登记实际食材批次、供餐和食品留样。'],
  CLEANER: ['保洁与消毒工作台', '负责所属食堂环境清洁和餐饮具消毒，记录完成情况并处理自己的任务。'],
  STAFF: ['我的任务与健康档案', '处理分派给自己的整改和调查任务，填写本人健康档案并提交总管审核。'],
  COMMUNITY: [`你好，${auth.profile?.displayName || '同学'}`, '提交校内食堂问题，查看处理状态与回复。']
})[role.value] : ['未授权账号', '请联系管理员配置岗位和食堂范围。'])

const metrics = computed(() => {
  switch (role.value) {
    case 'ADMIN': return [
      ['系统账号', users.value.length, '含师生账号'], ['已启用账号', users.value.filter(row => row.active).length, '停用账号不能登录'],
      ['校内食堂', canteens.value.length, '系统管理范围'], ['近期审计记录', audits.value.length, '最多 200 条']]
    case 'PRINCIPAL': return [
      ['未关闭问题', Number(summary.value.issueCount || 0) - Number(summary.value.closedIssueCount || 0), '全校范围'],
      ['逾期整改', summary.value.overdueIssueCount ?? 0, '需要关注'], ['已完成检查', summary.value.completedInspectionCount ?? 0, '全校累计'],
      ['月度调度', reviews.value.filter(row => row.period_type === 'MONTH').length, '人工确认留档']]
    case 'MANAGER': return [
      ['待验收批次', pendingBatches.value.length, '采购员负责验收'], ['已发布采购需求', publishedPlans.value.length, '查看采购执行进度'],
      ['未关闭整改', openIssues.value.length, '组织落实'], ['新反馈待受理', newFeedback.value.length, '直接进入总管待办']]
    case 'INSPECTOR': return [
      ['我的检查任务', myInspections.value.length, '待执行或进行中'], ['待复核整改', reviewIssues.value.length, '独立复核'],
      ['新反馈线索', newFeedback.value.length, '监管可查看'], ['高风险食堂', risks.value.filter(row => row.level === 'HIGH').length, '仅用于检查排序']]
    case 'PURCHASER': return [
      ['待执行采购需求', publishedPlans.value.length, '总管已发布'], ['待验收批次', pendingBatches.value.length, '采购到货后登记'],
      ['我的待整改', openIssues.value.length, '仅分派给本人'], ['个人健康档案', healthStatus.value, '本人填写，总管审核']]
    case 'CHEF': return [
      ['今日待制作', pendingCooking.value.length, '已发布食谱'], ['今日已制作', preparedMenus.value.length, '含已供餐菜品'],
      ['今日食品留样', todayOperations.value.filter(row => row.category === 'SAMPLE').length, '人工登记'], ['我的待整改', openIssues.value.length, '仅分派给本人']]
    case 'CLEANER': return [
      ['今日清洁记录', todayOperations.value.filter(row => row.category === 'CLEANING').length, '环境卫生'], ['今日消毒记录', todayOperations.value.filter(row => row.category === 'DISINFECTION').length, '餐饮具和设备'],
      ['我的待整改', openIssues.value.length, '仅分派给本人'], ['个人健康档案', healthStatus.value, '本人填写，总管审核']]
    case 'STAFF': return [
      ['我的待整改', openIssues.value.length, '仅分派给本人'], ['我的调查任务', processingFeedback.value.length, '配合总管调查'],
      ['个人健康档案', healthStatus.value, '本人填写，总管审核'], ['负责食堂', auth.profile?.canteenIds.length || 0, '账号授权范围']]
    default: return []
  }
})

const actions = computed(() => role.value ? ({
  ADMIN: [['/admin', '管理账号与权限', '创建账号、调整授权、停用账号'], ['/reports', '核对统计数据', '按食堂和日期导出报表']],
  PRINCIPAL: [['/issues', '发起问题督办', '监督整改，不代替监管复核'], ['/reviews', '确认月度调度', '阅读草稿并填写结论'], ['/reports', '查看全校报表', '比较三个食堂的处理情况']],
  MANAGER: [['/daily-menu', '编排与发布食谱', '按日期和餐次安排菜品，交给厨师制作'], ['/plans', '发布采购需求', '填写所需食材和数量，交给采购员执行'], ['/personnel', '审核人员健康档案', '查看员工提交的资料，通过或填写理由退回'], ['/menus', '维护菜品与原料', '管理菜品及标准原料，查看供餐执行'], ['/operations', '监督厨房与卫生', '查看留样、清洁和消毒执行记录'], ['/reviews', '组织周排查', '生成草稿并填写结论']],
  INSPECTOR: [['/inspections', '安排现场检查', '创建任务并逐项记录'], ['/issues', '复核整改', '通过或退回食堂提交的整改'], ['/feedback', '查看师生反馈', '监督食堂受理和回复'], ['/algorithms', '生成检查排程', '参考风险并人工确认']],
  PURCHASER: [['/plans', '查看采购需求', '按照总管发布的食材和数量采购'], ['/batches', '登记到货与验收', '核对数量、质量和凭证，填写验收结论'], ['/suppliers', '查询供应商与食材', '查看采购来源和证照']],
  CHEF: [['/daily-menu', '查看食谱并制作', '查看餐次、预计份数和制作要求，确认已制作'], ['/menus', '登记实际用料与供餐', '绑定验收合格批次后确认供餐'], ['/operations', '登记食品留样', '记录菜品、留样数量和存放位置']],
  CLEANER: [['/operations', '记录清洁与消毒', '记录清洁区域、消毒对象及完成时间'], ['/issues', '处理卫生整改任务', '提交分派给自己的整改情况']],
  STAFF: [['/issues', '处理我的整改', '提交分派给自己的整改情况'], ['/feedback', '处理我的调查', '向总管提交调查结果'], ['/personnel', '填写个人健康档案', '保存资料并提交总管审核，查看退回理由']],
  COMMUNITY: [['/feedback', '提交或查看反馈', '查看食堂处理进度与回复']]
})[role.value] : [])

async function load() {
  if (!role.value) return
  loading.value = true
  try {
    if (role.value === 'ADMIN') {
      const [a, b, c] = await Promise.all([api.get('/admin/users'), api.get('/canteens'), api.get('/admin/audit')])
      users.value = a.data; canteens.value = b.data; audits.value = c.data
    } else if (role.value === 'PRINCIPAL') {
      const [a, b, c, d] = await Promise.all([api.get('/reports/summary'), api.get('/risk'), api.get('/issues'), api.get('/reviews')])
      summary.value = a.data; risks.value = b.data; issues.value = c.data; reviews.value = d.data
      await nextTick(); drawRisk()
    } else if (role.value === 'MANAGER') {
      const [a, b, c, d, e] = await Promise.all([api.get('/issues'), api.get('/catalog/batches'), api.get('/feedback'), api.get('/catalog/plans'), api.get('/personnel/reviews')])
      issues.value = a.data; batches.value = b.data; feedback.value = c.data; plans.value = d.data; healthReviews.value = e.data
    } else if (role.value === 'INSPECTOR') {
      const [a, b, c, d] = await Promise.all([api.get('/inspections'), api.get('/issues'), api.get('/feedback'), api.get('/risk')])
      inspections.value = a.data; issues.value = b.data; feedback.value = c.data; risks.value = d.data
    } else if (isWorkerRole.value) {
      const requests = [api.get('/issues'), api.get('/feedback'), api.get('/personnel'), api.get('/personnel/my')]
      if (role.value === 'PURCHASER') requests.push(api.get('/catalog/plans'), api.get('/catalog/batches'))
      if (role.value === 'CHEF') requests.push(api.get('/catalog/menus'), api.get('/operations'))
      if (role.value === 'CLEANER') requests.push(api.get('/operations'))
      const [a, b, c, health, d, e] = await Promise.all(requests)
      issues.value = a!.data; feedback.value = b!.data; personnel.value = c!.data; healthSubmissions.value = health!.data
      if (role.value === 'PURCHASER') { plans.value = d!.data; batches.value = e!.data }
      if (role.value === 'CHEF') { menus.value = d!.data; operations.value = e!.data }
      if (role.value === 'CLEANER') operations.value = d!.data
    }
  } catch { ElMessage.error('工作台数据加载失败，请刷新重试') }
  finally { loading.value = false }
}

function drawRisk() {
  if (!chartEl.value) return
  chart ??= init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'axis' }, grid: { left: 45, right: 20, top: 20, bottom: 35 },
    xAxis: { type: 'category', data: risks.value.map(row => String(row.canteen_name)) },
    yAxis: { type: 'value', max: 100, name: '风险分' },
    series: [{ type: 'bar', data: risks.value.map(row => row.score ?? 0), barWidth: 38,
      itemStyle: { color: '#b78a52', borderRadius: [6, 6, 0, 0] } }]
  })
}
function resize() { chart?.resize() }
onMounted(() => { load(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>

<template>
  <div class="workspace-hero"><div><div class="workspace-eyebrow">{{ role === 'COMMUNITY' ? '师生服务' : '校园食安 · 专属工作台' }}</div><h1>{{ presentation[0] }}</h1><p>{{ presentation[1] }}</p></div><el-button v-if="role !== 'COMMUNITY'" plain :loading="loading" @click="load">刷新工作台</el-button><el-button v-else plain @click="router.push('/feedback')">我的反馈</el-button></div>
  <div v-if="role === 'COMMUNITY'" class="panel"><h2 class="panel-title">向食堂总管反馈问题</h2><p>你可以选择食堂并提交问题和现场图片，查看食堂的处理进度与回复；学校监管人员也能查看。</p><el-button type="primary" @click="router.push('/feedback')">提交反馈</el-button></div>
  <template v-else>
    <div class="workspace-metrics"><div v-for="item in metrics" :key="item[0]" class="workspace-metric"><span>{{ item[0] }}</span><strong>{{ item[1] }}</strong><small>{{ item[2] }}</small></div></div>
    <div class="workspace-grid"><div>
      <div v-if="role === 'ADMIN'" class="panel"><div class="section-heading"><h2>近期操作审计</h2><el-button text @click="router.push('/admin')">查看完整记录</el-button></div><div v-if="!audits.length" class="workspace-empty">暂无操作记录</div><div v-for="row in audits.slice(0, 6)" :key="row.id" class="workspace-list-row"><strong>{{ row.actor_name }} · {{ display(row.action) }}</strong><small>{{ display(row.created_at) }}</small></div></div>
      <template v-if="role === 'PRINCIPAL'"><div class="panel"><div class="section-heading"><h2>各食堂风险趋势</h2><el-button text @click="router.push('/algorithms')">查看评分依据</el-button></div><div ref="chartEl" class="workspace-chart"></div><p class="muted">风险分用于内部检查排序，不代表事故概率。</p></div><div class="panel"><div class="section-heading"><h2>重大与逾期问题</h2><el-button text @click="router.push('/issues')">进入督办</el-button></div><div v-if="!dueIssues.length" class="workspace-empty">暂无重大或逾期问题</div><div v-for="row in dueIssues.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.canteen_name }} · {{ row.description }}</strong><small>{{ row.overdue ? '已逾期' : '重大事项' }}</small></div></div></template>
      <template v-if="role === 'MANAGER'"><div class="panel"><div class="section-heading"><h2>到货与采购执行</h2><el-button text @click="router.push('/batches')">查看验收记录</el-button></div><p class="muted">采购员执行采购与验收，总管查看进度并督促完成。</p><div v-if="!pendingBatches.length" class="workspace-empty">暂无待验收批次</div><div v-for="row in pendingBatches.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.ingredient_name }} · 批次 {{ batchNumber(row) }}</strong><small>{{ display(row.received_on) }}</small></div></div><div class="panel"><div class="section-heading"><h2>本食堂整改</h2><el-button text @click="router.push('/issues')">查看全部</el-button></div><div v-if="!openIssues.length" class="workspace-empty">暂无未关闭问题</div><div v-for="row in openIssues.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.description }}</strong><small>{{ row.overdue ? '已逾期' : row.status === 'REVIEW' ? '待监管复核' : '待整改' }}</small></div></div></template>
      <template v-if="role === 'INSPECTOR'"><div class="panel"><div class="section-heading"><h2>我的检查任务</h2><el-button text @click="router.push('/inspections')">安排与执行</el-button></div><div v-if="!myInspections.length" class="workspace-empty">暂无待执行任务</div><div v-for="row in myInspections.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.canteen_name }} · {{ row.status === 'SCHEDULED' ? '待执行' : '进行中' }}</strong><small>{{ display(row.scheduled_at) }}</small></div></div><div class="panel"><div class="section-heading"><h2>待复核的整改</h2><el-button text @click="router.push('/issues')">进入复核</el-button></div><div v-if="!reviewIssues.length" class="workspace-empty">暂无待复核记录</div><div v-for="row in reviewIssues.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.canteen_name }} · {{ row.description }}</strong><small>待复核</small></div></div></template>
      <template v-if="role === 'PURCHASER'">
        <div class="panel"><div class="section-heading"><h2>总管发布的采购需求</h2><el-button text @click="router.push('/plans')">查看需求</el-button></div><div v-if="!publishedPlans.length" class="workspace-empty">暂无待执行采购需求</div><div v-for="row in publishedPlans.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.ingredient_name }} · {{ row.planned_quantity }} {{ row.ingredient_unit }}</strong><small>{{ row.canteen_name }} · {{ display(row.planned_on) }}</small></div></div>
        <div class="panel"><div class="section-heading"><h2>到货待验收</h2><el-button text @click="router.push('/batches')">前往验收</el-button></div><div v-if="!pendingBatches.length" class="workspace-empty">暂无待验收批次</div><div v-for="row in pendingBatches.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.ingredient_name }} · 批次 {{ batchNumber(row) }}</strong><small>{{ row.supplier_name }} · {{ display(row.received_on) }}</small></div></div>
      </template>
      <div v-if="role === 'CHEF'" class="panel"><div class="section-heading"><h2>今日待制作菜品</h2><el-button text @click="router.push('/daily-menu')">查看食谱</el-button></div><div v-if="!pendingCooking.length" class="workspace-empty">今日暂无待制作的已发布菜品</div><div v-for="row in pendingCooking.slice(0, 8)" :key="row.id" class="workspace-list-row"><strong>{{ display(row.meal) }} · {{ row.dish_name }} · {{ row.target_portions ? `${row.target_portions} 份` : '份数未填写' }}</strong><small>{{ row.cooking_note || '按标准原料与制作流程完成' }}</small></div><p class="muted">制作完成后登记实际食材批次与供餐，并填写食品留样记录。</p></div>
      <div v-if="role === 'CLEANER'" class="panel"><div class="section-heading"><h2>今日清洁与消毒记录</h2><el-button text @click="router.push('/operations')">登记完成情况</el-button></div><div v-if="!todayOperations.length" class="workspace-empty">今日尚未登记清洁或消毒记录</div><div v-for="row in todayOperations.slice(0, 6)" :key="row.id" class="workspace-list-row"><strong>{{ row.canteen_name }} · {{ row.category === 'CLEANING' ? '环境清洁' : '餐饮具消毒' }}</strong><small>{{ row.recorder_name }} · {{ display(row.created_at) }}</small></div><p class="muted">请记录各区域的清洁和餐饮具、设备的消毒情况。</p></div>
      <div v-if="isWorkerRole" class="panel"><div class="section-heading"><h2>分派给我的整改</h2><el-button text @click="router.push('/issues')">查看任务</el-button></div><div v-if="!openIssues.length" class="workspace-empty">暂无分派给你的待整改问题</div><div v-for="row in openIssues.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.description }}</strong><small>{{ row.overdue ? '已逾期' : row.status === 'REVIEW' ? '待监管复核' : '待处理' }}</small></div></div>
    </div><div>
      <div class="panel"><h2 class="panel-title">{{ role === 'ADMIN' ? '系统管理入口' : role === 'PRINCIPAL' ? '校级决策入口' : role === 'MANAGER' ? '运营管理入口' : role === 'INSPECTOR' ? '监管业务入口' : role === 'PURCHASER' ? '采购业务入口' : role === 'CHEF' ? '厨房业务入口' : role === 'CLEANER' ? '保洁业务入口' : '我的任务入口' }}</h2><div class="workspace-actions"><button v-for="item in actions" :key="item[1]" class="workspace-action" type="button" @click="router.push(item[0]!)"><strong>{{ item[1] }} →</strong><small>{{ item[2] }}</small></button></div></div>
      <div v-if="role === 'ADMIN'" class="panel"><h2 class="panel-title">账号角色分布</h2><div v-for="(label, roleName) in roleLabels" :key="roleName" class="workspace-list-row"><strong>{{ label }}</strong><small>{{ users.filter(row => row.roles?.includes(roleName)).length }} 人</small></div></div>
      <div v-if="role === 'PRINCIPAL'" class="panel"><h2 class="panel-title">管理边界</h2><p>校长可查看全校数据、发起督办并确认调度结论。检查结论与整改复核仍由监管人员完成。</p></div>
      <div v-if="role === 'MANAGER'" class="panel"><h2 class="panel-title">反馈待办</h2><p>新反馈 {{ newFeedback.length }} 条，处理中 {{ processingFeedback.length }} 条。总管负责受理、安排调查，并向提交人回复。</p><el-button v-if="newFeedback.length || processingFeedback.length" text @click="router.push('/feedback')">前往处理 →</el-button></div>
      <div v-if="role === 'MANAGER'" class="panel"><h2 class="panel-title">健康档案审核待办</h2><p>待审核 {{ pendingHealthReviews.length }} 份。核对工作人员自己填写的资料和健康证明，通过后纳入人员健康档案；退回时请填写理由。</p><el-button text @click="router.push('/personnel')">进入人员健康与审核 →</el-button></div>
      <div v-if="role === 'INSPECTOR'" class="panel"><h2 class="panel-title">反馈监督查看</h2><div v-if="!newFeedback.length" class="workspace-empty">暂无新反馈线索</div><div v-for="row in newFeedback.slice(0, 5)" :key="row.id" class="workspace-list-row"><strong>{{ row.canteen_name }} · {{ display(row.category) }}</strong><small>食堂待受理</small></div><el-button v-if="newFeedback.length" text @click="router.push('/feedback')">查看处理情况 →</el-button></div>
      <div v-if="isWorkerRole" class="panel"><h2 class="panel-title">岗位职责</h2><p v-if="role === 'PURCHASER'">采购员按照总管发布的需求采购和验收食材，填写到货凭证、数量及验收结论。</p><p v-else-if="role === 'CHEF'">厨师按照总管发布的食谱做菜，登记实际用料、供餐与食品留样。</p><p v-else-if="role === 'CLEANER'">保洁员负责所属食堂全部环境清洁、餐饮具与设备消毒，并记录完成情况。</p><p v-else>处理本人获派的整改和调查任务。</p><p>本人填写健康档案并提交食堂总管审核；退回后按理由修改，再次提交。</p><p class="muted">我的调查任务 {{ processingFeedback.length }} 条 · 已通过健康证明：{{ healthStatus }} · 最新申请：{{ healthApplicationStatus }}</p><div class="actions"><el-button text @click="router.push('/feedback')">我的调查任务 →</el-button><el-button text @click="router.push('/personnel')">填写与查看我的健康档案 →</el-button></div></div>
    </div></div>
  </template>
</template>
