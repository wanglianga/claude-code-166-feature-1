<template>
  <div>
    <div class="head-line">
      <h2 class="page-title" style="margin:0">上门未遇处理（改约 · 空跑费 · 暂停病床/复评恢复）</h2>
      <el-radio-group v-model="filter" size="default" @change="load">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="PENDING_FAMILY">待家属说明</el-radio-button>
        <el-radio-button label="FAMILY_EXPLAINED">家属已说明</el-radio-button>
        <el-radio-button label="BED_PAUSED">病床暂停待复评</el-radio-button>
        <el-radio-button label="REVIEWED">已审核</el-radio-button>
        <el-radio-button label="REASSESSED">已复评恢复</el-radio-button>
        <el-radio-button label="WAIVED">已减免</el-radio-button>
      </el-radio-group>
    </div>

    <div class="card-soft">
      <el-table :data="cases" v-loading="loading" size="small">
        <el-table-column prop="caseNo" label="工单号" width="170" />
        <el-table-column label="患者" width="100"><template #default="{row}">{{ row.patient.name }}</template></el-table-column>
        <el-table-column label="护士" width="90"><template #default="{row}">{{ row.nurse?.name || '-' }}</template></el-table-column>
        <el-table-column label="未遇原因" width="130">
          <template #default="{row}"><el-tag size="small" type="danger" effect="plain">{{ label('NoShowReason', row.reason) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="等待/请假" width="110">
          <template #default="{row}">
            <div>{{ row.waitMinutes }} 分钟</div>
            <div class="muted">{{ row.advanceLeaveNotified ? '已提前请假' : '未提前请假' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="125">
          <template #default="{row}"><el-tag size="small" :type="tagType('NoShowStatus',row.status)">{{ label('NoShowStatus', row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="审核结论" show-overflow-tooltip>
          <template #default="{row}">
            <span v-if="row.decision">{{ label('NoShowDecision', row.decision) }}<span v-if="row.emptyRunFee>0"> ¥{{ row.emptyRunFee }}</span></span>
            <span v-else-if="row.familyNote">家属已说明，待审核</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="open(row)">查看/办理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 工单详情与办理 -->
    <el-drawer v-model="detailVisible" size="620px" :title="'上门未遇工单 ' + (detail?.noShow?.caseNo || '')">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="患者">{{ detail.noShow.patient.name }}（{{ detail.noShow.patient.age }}岁）</el-descriptions-item>
          <el-descriptions-item label="责任护士">{{ detail.noShow.nurse?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="未遇原因">{{ label('NoShowReason', detail.noShow.reason) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="tagType('NoShowStatus', detail.noShow.status)">{{ label('NoShowStatus', detail.noShow.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="到达时间">{{ fmt(detail.noShow.arrivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="离开时间">{{ fmt(detail.noShow.leftAt) }}</el-descriptions-item>
          <el-descriptions-item label="门口等待">{{ detail.noShow.waitMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="提前请假">{{ detail.noShow.advanceLeaveNotified ? '是' : '否（可计空跑费/扣次）' }}</el-descriptions-item>
          <el-descriptions-item label="到达定位" :span="2">{{ detail.noShow.arrivalLocationSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="护士说明" :span="2">{{ detail.noShow.nurseNote || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title mt-16">电话拨打记录（{{ detail.phoneCalls.length }}）</div>
        <el-table :data="detail.phoneCalls" size="small" border>
          <el-table-column prop="contact" label="联系人" width="120" />
          <el-table-column prop="phone" label="电话" width="130" />
          <el-table-column prop="result" label="结果" width="110" />
          <el-table-column label="通话时长" width="90">
            <template #default="{row}">{{ row.durationSeconds ? row.durationSeconds + ' 秒' : '未接通' }}</template>
          </el-table-column>
        </el-table>

        <div class="section-title mt-16">门口照片证据</div>
        <el-image v-for="(p,i) in detail.doorPhotoPaths" :key="i" :src="p"
                  :preview-src-list="detail.doorPhotoPaths" class="photo-thumb" fit="cover" />

        <!-- 家属说明 -->
        <el-divider content-position="left">家属补充说明</el-divider>
        <template v-if="detail.noShow.familyNote">
          <el-alert type="info" :closable="false" show-icon
                    :title="label('NoShowFamilyReason', detail.noShow.familyReason)">
            <div>{{ detail.noShow.familyNote }}</div>
            <div class="muted">提交时间：{{ fmt(detail.noShow.familySubmittedAt) }}</div>
            <el-image v-if="detail.noShow.familyEvidencePath" :src="detail.noShow.familyEvidencePath"
                      :preview-src-list="[detail.noShow.familyEvidencePath]" class="photo-thumb" fit="cover" />
          </el-alert>
        </template>
        <el-empty v-else description="家属尚未提交补充说明（空跑费生成前须先开放家属说明）" :image-size="60" />

        <!-- 家属办理：提交说明 -->
        <div v-if="canExplain(detail.noShow)" class="action-box">
          <el-select v-model="famForm.familyReason" placeholder="选择未遇事由" style="width:240px">
            <el-option v-for="(v,k) in ENUMS.NoShowFamilyReason" :key="k" :label="v" :value="k" />
          </el-select>
          <el-input v-model="famForm.familyNote" type="textarea" :rows="3" class="mt-12"
                    placeholder="如：临时急诊送医 / 手机没电 / 地址变更，请如实说明（可上传急诊挂号单等凭证）" />
          <div class="mt-12"><UploadPhotos v-model="famForm.evidence" type="evidence" /></div>
          <el-button type="primary" class="mt-12" @click="submitExplanation">提交补充说明</el-button>
        </div>

        <!-- 卫生院审核 -->
        <template v-if="canReview(detail.noShow)">
          <el-divider content-position="left">卫生院审核（减免 / 空跑费 / 扣除一次服务 / 调整下次 / 暂停病床）</el-divider>
          <el-radio-group v-model="revForm.decision" class="decision-group">
            <el-radio-button v-for="(v,k) in ENUMS.NoShowDecision" :key="k" :value="k">{{ v }}</el-radio-button>
          </el-radio-group>
          <div v-if="revForm.decision==='CHARGE_EMPTY_RUN'" class="mt-12">
            空跑费金额 ¥
            <el-input-number v-model="revForm.emptyRunFee" :min="0" :precision="2" :step="10" controls-position="right" />
            <span class="muted">（默认取收费目录“空跑费”¥{{ defaultFee }}，可调整）</span>
          </div>
          <div v-if="['CHARGE_EMPTY_RUN','DEDUCT_SERVICE','ADJUST_NEXT','WAIVE'].includes(revForm.decision)" class="mt-12">
            可选改约到：<el-date-picker v-model="revForm.rescheduleTime" type="datetime" placeholder="不改期则空（本次任务关闭）" />
            <div class="muted">不填改期时间时，本次未遇任务关闭/并入下次巡诊；空跑费仍按账期进入统一账单。</div>
          </div>
          <el-input v-model="revForm.decisionNote" type="textarea" :rows="3" class="mt-12"
                    placeholder="审核意见：核实过程、减免或收费依据、是否提前请假等" />
          <el-button type="primary" class="mt-12" @click="submitReview">提交审核决定</el-button>
        </template>

        <!-- 医生复评恢复 -->
        <template v-if="canReassess(detail.noShow)">
          <el-divider content-position="left">家庭医生重新评估后恢复巡诊</el-divider>
          <el-alert type="warning" :closable="false" class="mb12" show-icon
                    title="家庭病床当前为暂停状态。请在患者出院/病情稳定并重新评估后，填写结论并恢复。" />
          <el-input v-model="reassessForm.reassessNote" type="textarea" :rows="3"
                    placeholder="复评结论：如已出院、血压控制稳定，可恢复每周 2 次巡诊" />
          <div class="mt-12">
            恢复并安排上门：<el-date-picker v-model="reassessForm.resumeTime" type="datetime" placeholder="选择恢复后的首次上门时间" />
            <div class="muted">不选时间则本次未遇任务并入后续计划，病床与巡诊计划直接恢复。</div>
          </div>
          <el-button type="success" class="mt-12" @click="submitReassess">复评通过，恢复病床与巡诊</el-button>
        </template>

        <!-- 已审核/复评结论 -->
        <template v-if="detail.noShow.decision && detail.noShow.status!=='BED_PAUSED'">
          <el-divider content-position="left">处理记录</el-divider>
          <el-alert type="success" :closable="false"
                    :title="label('NoShowDecision', detail.noShow.decision) + (detail.noShow.emptyRunFee>0 ? ' ¥'+detail.noShow.emptyRunFee : '')">
            <div>{{ detail.noShow.decisionNote }}</div>
            <div class="muted">审核人：{{ detail.noShow.reviewer?.name }} ｜ {{ fmt(detail.noShow.reviewedAt) }}
              <span v-if="detail.noShow.billed"> ｜ 空跑费已计入统一账单</span></div>
          </el-alert>
        </template>
        <el-alert v-if="detail.noShow.status==='REASSESSED'" type="success" class="mt-12" :closable="false"
                  :title="'医生复评已恢复：' + detail.noShow.reassessDoctor?.name">
          <div>{{ detail.noShow.reassessNote }}</div>
          <div class="muted">复评时间：{{ fmt(detail.noShow.reassessedAt) }}</div>
        </el-alert>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from '../util/dayjs-lite'
import api from '../api'
import { useAuthStore } from '../store/auth'
import { ENUMS, label, tagType } from '../labels'
import UploadPhotos from '../components/UploadPhotos.vue'

const auth = useAuthStore()
const cases = ref([])
const loading = ref(true)
const filter = ref('')
const detailVisible = ref(false)
const detail = ref(null)
const defaultFee = ref(30)

const famForm = reactive({ familyReason: null, familyNote: '', evidence: [] })
const revForm = reactive({ decision: 'WAIVE', decisionNote: '', emptyRunFee: 30, rescheduleTime: null })
const reassessForm = reactive({ reassessNote: '', resumeTime: null })

async function load() {
  loading.value = true
  cases.value = (await api.get('/no-show' + (filter.value ? '?status=' + filter.value : ''))).data
  loading.value = false
}
onMounted(load)

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '-' }

async function open(row) {
  detail.value = (await api.get(`/no-show/${row.id}`)).data
  const ns = detail.value.noShow
  Object.assign(famForm, { familyReason: ns.familyReason || null, familyNote: '', evidence: [] })
  Object.assign(revForm, {
    decision: ns.status === 'BED_PAUSED' ? 'SUSPEND_BED' : 'CHARGE_EMPTY_RUN',
    decisionNote: '', emptyRunFee: ns.emptyRunFee || defaultFee.value, rescheduleTime: null
  })
  Object.assign(reassessForm, { reassessNote: '', resumeTime: null })
  detailVisible.value = true
}

function canExplain(ns) {
  if (auth.isFamily) {
    return ['PENDING_FAMILY', 'FAMILY_EXPLAINED', 'BED_PAUSED'].includes(ns.status)
  }
  // 管理者也可代家属补录说明
  return auth.isAdmin && ['PENDING_FAMILY', 'FAMILY_EXPLAINED'].includes(ns.status)
}
function canReview(ns) {
  return (auth.isAdmin || auth.isFinance)
    && ['PENDING_FAMILY', 'FAMILY_EXPLAINED', 'BED_PAUSED'].includes(ns.status)
}
function canReassess(ns) {
  return (auth.isDoctor || auth.isAdmin) && ns.status === 'BED_PAUSED'
}

async function submitExplanation() {
  if (!famForm.familyReason || !famForm.familyNote) return ElMessage.warning('请选择事由并填写补充说明')
  await api.post(`/no-show/${detail.value.noShow.id}/family-explanation`, {
    familyReason: famForm.familyReason,
    familyNote: famForm.familyNote,
    familyEvidencePath: famForm.evidence[0] || null
  })
  ElMessage.success('补充说明已提交，等待卫生院审核')
  detailVisible.value = false
  load()
}

async function submitReview() {
  if (!revForm.decisionNote) return ElMessage.warning('请填写审核意见')
  const body = {
    decision: revForm.decision,
    decisionNote: revForm.decisionNote,
    emptyRunFee: revForm.decision === 'CHARGE_EMPTY_RUN' ? revForm.emptyRunFee : null,
    rescheduleTime: revForm.rescheduleTime ? dayjs(revForm.rescheduleTime) : null
  }
  await api.post(`/no-show/${detail.value.noShow.id}/review`, body)
  ElMessage.success(revForm.decision === 'SUSPEND_BED'
    ? '家庭病床已暂停，等待家庭医生重新评估'
    : '审核决定已提交')
  detailVisible.value = false
  load()
}

async function submitReassess() {
  if (!reassessForm.reassessNote) return ElMessage.warning('请填写复评结论')
  await api.post(`/no-show/${detail.value.noShow.id}/reassess`, {
    reassessNote: reassessForm.reassessNote,
    resumeTime: reassessForm.resumeTime ? dayjs(reassessForm.resumeTime) : null
  })
  ElMessage.success('复评通过，家庭病床与巡诊已恢复')
  detailVisible.value = false
  load()
}
</script>

<style scoped>
.head-line { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.section-title { font-weight: 600; margin-bottom: 8px; }
.mt-12 { margin-top: 12px; } .mt-16 { margin-top: 16px; } .mb12 { margin-bottom: 12px; }
.photo-thumb { width: 130px; height: 90px; border-radius: 6px; margin: 4px 8px 4px 0; border: 1px solid #ddd; }
.action-box { background: #f4f8f6; border: 1px solid #d8e8e2; border-radius: 8px; padding: 12px; margin-top: 12px; }
.decision-group { display: flex; flex-wrap: wrap; gap: 6px; }
</style>
