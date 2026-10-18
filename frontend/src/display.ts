const labels: Record<string, string> = {
  PENDING: '待处理', ACCEPTED: '合格', REJECTED: '未通过', STOPPED: '已停用',
  NEW: '待受理', PROCESSING: '处理中', RESPONDED: '已回复', CLOSED: '已关闭',
  OPEN: '进行中', REVIEW: '待复核', APPROVED: '已通过',
  SCHEDULED: '待执行', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消',
  PLANNED: '计划中', SERVED: '已供餐', DRAFT: '草稿', CONFIRMED: '已确认',
  SUGGESTED: '待确认', DISMISSED: '已忽略',
  PASS: '正常', FAIL: '异常', NA: '不适用', NONE: '不适用',
  BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐',
  HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险', INSUFFICIENT: '数据不足',
  WEEK: '周排查', MONTH: '月调度',
  CLEANING: '环境清洁', DISINFECTION: '餐具消毒', SAMPLE: '食品留样',
  ADMIN: '超级管理员', PRINCIPAL: '校长', MANAGER: '食堂总管', INSPECTOR: '监管人员',
  STAFF: '食堂工作人员', PURCHASER: '采购员', CHEF: '厨师', CLEANER: '保洁员', COMMUNITY: '师生',
  FEEDBACK_IMAGE: '反馈图片', BATCH_VOUCHER: '采购凭证', SUPPLIER_CERT: '供应商证照',
  ACCEPTANCE_EVIDENCE: '验收凭证', ISSUE_EVIDENCE: '问题证据',
  RECTIFICATION_EVIDENCE: '整改证据', STAFF_HEALTH_CERT: '健康证明',
  MANUAL: '人工发现', INSPECTION: '现场检查', FEEDBACK: '师生反馈', DAILY_CHECK: '每日检查', BATCH: '食材批次',
  REPORTED: '已上报', DISCOVERED: '已发现', FOLLOW_UP: '跟进处理', MEASURE: '处置措施',
  UNSPECIFIED: '未填写', MALE: '男', FEMALE: '女',
  issues: '检查问题', overdue: '逾期整改', repeat: '重复问题', certificate: '过期证照',
  rejection: '拒收批次',
  CREATE_USER: '创建账号', CHANGE_USER_ACCESS: '调整账号权限', SET_USER_ACTIVE: '启用或停用账号',
  RESET_PASSWORD: '重置密码', SELF_REGISTER: '师生注册',
  CREATE_CANTEEN: '新增食堂', UPDATE_CANTEEN: '修改食堂', CREATE_STALL: '新增档口', UPDATE_STALL: '修改档口',
  CREATE_CHECK_TEMPLATE: '新增检查模板', ADD_CHECK_ITEM: '新增检查项', SET_CHECK_ITEM_ACTIVE: '调整检查项状态',
  SET_TEMPLATE_ACTIVE: '调整模板状态', CREATE_SUPPLIER: '新增供应商', UPDATE_SUPPLIER: '修改供应商',
  CREATE_INGREDIENT: '新增食材', CREATE_BATCH: '登记食材批次', ACCEPT_BATCH: '验收食材批次',
  STOP_BATCH: '停用食材批次', CREATE_DISH: '新增菜品', ADD_DISH_INGREDIENT: '设置菜品原料',
  CREATE_MENU: '创建供餐菜单', USE_BATCH: '登记实际用料', SERVE_MENU: '确认供餐', CANCEL_MENU: '撤销供餐',
  CREATE_DAILY_CHECK: '提交每日检查', CREATE_INSPECTION: '创建检查任务', START_INSPECTION: '开始检查',
  COMPLETE_INSPECTION: '完成检查', CANCEL_INSPECTION: '取消检查', CREATE_ISSUE: '创建整改问题',
  SUBMIT_RECTIFICATION: '提交整改', REVIEW_RECTIFICATION: '复核整改', ADD_ISSUE_EVENT: '记录问题进展',
  CREATE_FEEDBACK: '提交反馈', ASSIGN_FEEDBACK: '分派反馈', INVESTIGATE_FEEDBACK: '调查反馈',
  RESPOND_FEEDBACK: '回复反馈', CLOSE_FEEDBACK: '结案反馈', REOPEN_FEEDBACK: '申请继续处理',
  CREATE_PURCHASE_PLAN: '创建采购计划', CLOSE_PURCHASE_PLAN: '关闭采购计划',
  PUBLISH_PURCHASE_PLAN: '发布采购需求', PUBLISH_DAILY_MENU: '发布每日食谱',
  UPDATE_MENU_DRAFT: '修改食谱草稿', PREPARE_MENU: '确认菜品制作完成',
  SAVE_STAFF_PROFILE: '维护人员档案', CREATE_OPERATION_LOG: '记录现场操作',
  SAVE_STAFF_PROFILE_DRAFT: '保存本人健康档案草稿', SUBMIT_STAFF_PROFILE: '提交本人健康档案',
  APPROVE_STAFF_PROFILE: '审核通过健康档案', REJECT_STAFF_PROFILE: '退回健康档案',
  GENERATE_PERIOD_REVIEW: '生成周期报告', CONFIRM_PERIOD_REVIEW: '确认周期报告',
  CREATE_OVERSIGHT: '发起督办', CLOSE_OVERSIGHT: '结束督办',
  CHANGE_RISK_CONFIG: '调整风险参数', GENERATE_SCHEDULE: '生成排程建议',
  ADJUST_SCHEDULE: '调整排程建议', CONFIRM_SCHEDULE: '确认排程', DISMISS_SCHEDULE: '忽略排程',
  SET_INSPECTOR_AVAILABILITY: '设置监管可用时间', EXPORT_REPORT: '导出报表',
  UPLOAD_ATTACHMENT: '上传附件', VIEW_ATTACHMENT: '查看附件', TRACE_BATCH: '查询批次追溯',
  TRACE_MENU: '查询供餐追溯',
  app_user: '账号', canteen: '食堂', stall: '档口', check_template: '检查模板', check_item: '检查项',
  supplier: '供应商', ingredient: '食材', purchase_batch: '食材批次', acceptance: '批次验收',
  dish: '菜品', menu_entry: '供餐记录', batch_usage: '批次领用', daily_check: '每日检查',
  inspection: '检查任务', issue: '整改问题', rectification: '整改记录', feedback: '师生反馈',
  purchase_plan: '采购计划', staff_profile: '人员档案', operation_log: '现场记录',
  staff_profile_submission: '健康档案申请',
  period_review: '周期报告', issue_oversight: '校长督办', schedule_suggestion: '排程建议',
  risk_config: '风险参数', uploaded_file: '附件', report: '统计报表'
}

const auditKeys: Record<string, string> = {
  id: '编号', canteen: '食堂编号', canteens: '食堂范围', roles: '角色', assignee: '处理人编号',
  issue: '问题编号', rectification: '整改编号', ingredient: '食材编号', dish: '菜品编号',
  batch: '批次编号', batches: '批次数', menu: '供餐记录编号', quantity: '数量',
  affectedMenus: '关联供餐数', week: '周起始日', assigned: '已分派数', date: '日期',
  inspector: '监管人员编号', inspection: '检查任务编号', scope: '食堂范围',
  from: '开始日期', to: '结束日期', before: '修改前', after: '修改后', active: '启用',
  purpose: '附件用途', version: '版本', decision: '审核结果'
}

export function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const text = String(value)
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)) {
    if (/(?:Z|[+-]\d{2}:?\d{2})$/.test(text)) {
      const instant = new Date(text)
      if (!Number.isNaN(instant.getTime())) {
        const parts = new Intl.DateTimeFormat('en-CA', {
          timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
          hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
        }).formatToParts(instant)
        const part = (name: string) => parts.find(item => item.type === name)?.value || ''
        return `${part('year')}-${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`
      }
    }
    return text.replace('T', ' ').slice(0, 16)
  }
  return labels[text] || text
}

export function batchNumber(row: Record<string, unknown> | null | undefined): string {
  return row?.id === null || row?.id === undefined ? '—' : String(row.id)
}

export function displayAuditDetail(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
    .replace(/\b([A-Z][A-Z_]+)\b/g, code => labels[code] || code)
    .replace(/\b([a-z][A-Za-z]+)=/g, (_, key: string) => `${auditKeys[key] || key}=`)
    .replace(/\btrue\b/g, '是').replace(/\bfalse\b/g, '否')
}
