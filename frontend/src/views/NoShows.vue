<template>
  <div>
    <div class="head-line">
      <h2 class="page-title" style="margin:0">上门未遇改约与费用处理</h2>
      <el-radio-group v-model="tab" size="default">
        <el-radio-button label="noshow">未遇 / 空跑费用</el-radio-button>
        <el-radio-button v-if="canPause" label="pause">病床暂停与复评</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ============ 未遇记录 ============ -->
    <template v-if="tab === 'noshow'">
      <el-alert type="info" :closable="false" show-icon class="mb12"
                title="护士上门但家属未开门时，必须留存到达定位、电话拨打、等待时长与门口照片；家属可在费用核定前提交补充说明，卫生院审核后决定减免、保留空跑费、扣除一次服务或调整下一次巡诊。" />
      <div class="card-soft">
        <el-radio-group v-model="filter" size="default" class="mb12" @change="loadNoShows">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="WAITING_EXCUSE">待家属说明</el-radio-button>
          <el-radio-button label="EXPLAINED">家属已说明</el-radio-button>
          <el-radio-button label="REVIEWED">卫生院已审核</el-radio-button>
        </el-radio-group>
        <el-table :data="noShows" v-loading="loading" size="small">
          <el-table-column prop="noShowNo" label="单号" width="160" />
          <el-table-column label="患者" width="90"><template #default="{row}">{{ row.patient.name }}</template></el-table-column>
          <el-table-column label="护士" width="80"><template #default="{row}">{{ row.nurse?.name || '-' }}</template></el-table-column>
          <el-table-column label="未遇原因" width="130">
            <template #default="{row}">{{ label('NoShowReason', row.reason) }}</template>
          </el-table-column>
          <el-table-column label="等待/电话" width="110">
            <template #default="{row}">
              <div>{{ row.waitMinutes }} 分钟</div>
              <div class="muted">拨 {{ row.callCount }} 次 / 通 {{ row.connectedCallCount }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{row}">
              <el-tag size="small" :type="tagType('NoShowStatus',row.status)">{{ label('NoShowStatus',row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核决定" min-width="150" show-overflow-tooltip>
            <template #default="{row}">
              <span v-if="row.decision">{{ label('NoShowDecision', row.decision) }}
                <span v-if="row.approvedFee > 0" class="fee">¥{{ row.approvedFee }}</span>
                <span v-if="row.deductServiceCount > 0" class="muted">（扣 {{ row.deductServiceCount }} 次）</span>
              </span>
              <span v-else class="muted">待卫生院审核</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{row}">
              <el-button link type="info" size="small" @click="viewNoShow(row)">证据/详情</el-button>
              <el-button v-if="auth.isFamily && canExcuse(row)" link type="primary" size="small" @click="openExcuse(row)">补充说明</el-button>
              <el-button v-if="canReview && row.status!=='REVIEWED' && row.status!=='CLOSED'"
                         link type="warning" size="small" @click="openReview(row)">审核处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ============ 病床暂停 / 复评 ============ -->
    <template v-else>
      <el-alert type="warning" :closable="false" show-icon class="mb12"
                title="患者临时送医时家庭病床自动置为“暂停巡诊”，后续任务挂起；须由家庭医生重新评估，复评通过后才恢复巡诊。" />
      <div class="card-soft">
        <el-radio-group v-model="pauseFilter" size="default" class="mb12" @change="loadPauses">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="PAUSED">暂停待复评</el-radio-button>
          <el-radio-button label="REEVALUATED">医生已复评</el-radio-button>
          <el-radio-button label="RESUMED">已恢复巡诊</el-radio-button>
        </el-radio-group>
        <el-table :data="pauses" v-loading="pauseLoading" size="small">
          <el-table-column prop="pauseNo" label="暂停单号" width="160" />
          <el-table-column label="患者" width="90"><template #default="{row}">{{ row.patient.name }}</template></el-table-column>
          <el-table-column label="建床号" width="100">
            <template #default="{row}">{{ row.application.bedNo || row.application.applyNo }}</template>
          </el-table-column>
          <el-table-column label="原因" width="140">
            <template #default="{row}">{{ label('BedPauseReason', row.reason) }}</template>
          </el-table-column>
          <el-table-column prop="pauseNote" label="暂停说明" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{row}">
              <el-tag size="small" :type="tagType('BedPauseStatus',row.status)">{{ label('BedPauseStatus',row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{row}">
              <el-button v-if="auth.isDoctor && row.status==='PAUSED'" link type="primary" size="small" @click="openReevaluate(row)">重新评估</el-button>
              <el-button v-if="auth.isDoctor && row.status==='REEVALUATED'" link type="warning" size="small" @click="openResume(row)">复评恢复</el-button>
              <el-button v-if="auth.isAdmin && row.status==='PAUSED'" link type="primary" size="small" @click="openReevaluate(row)">代为评估</el-button>
              <el-button v-if="auth.isAdmin && row.status!=='RESUMED'" link type="warning" size="small" @click="openResume(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 未遇证据详情 -->
    <el-drawer v-model="detailVisible" size="600px" :title="'上门未遇 ' + (detail?.noShow?.noShowNo || '')">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="患者">{{ detail.noShow.patient.name }}（{{ detail.noShow.patient.address }}）</el-descriptions-item>
          <el-descriptions-item label="未遇原因">{{ label('NoShowReason', detail.noShow.reason) }}，家属提前请假：{{ detail.noShow.earlyLeaveApproved ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="到达时间">{{ fmt(detail.noShow.arrivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="到达定位">{{ detail.noShow.arrivalLocation || '-' }}
            <span v-if="detail.noShow.arrivalLatitude" class="muted">（{{ detail.noShow.arrivalLongitude }}, {{ detail.noShow.arrivalLatitude }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="等待时长">{{ detail.noShow.waitMinutes }} 分钟（{{ fmt(detail.noShow.waitStartedAt) }} ~ {{ fmt(detail.noShow.waitEndedAt) }}）</el-descriptions-item>
          <el-descriptions-item label="护士说明">{{ detail.noShow.nurseNote || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title mt-16">电话拨打记录（{{ detail.callLogs.length }}）</div>
        <el-table :data="detail.callLogs" size="small" border>
          <el-table-column prop="phone" label="号码" />
          <el-table-column label="结果" width="90">
            <template #default="{row}">
              <el-tag size="small" :type="row.connected ? 'success' : 'danger'">{{ row.connected ? '已接通' : '未接通' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时长" width="80"><template #default="{row}">{{ row.connected ? (row.durationSec || 0) + '秒' : '-' }}</template></el-table-column>
          <el-table-column label="拨打时间"><template #default="{row}">{{ fmt(row.time) }}</template></el-table-column>
        </el-table>

        <div class="section-title mt-16">门口照片</div>
        <el-image v-for="(p,i) in detail.doorPhotoPaths" :key="i" :src="p"
                  :preview-src-list="detail.doorPhotoPaths" class="door-thumb" fit="cover" />

        <div class="section-title mt-16">家属补充说明（{{ detail.excuses.length }}）</div>
        <el-empty v-if="!detail.excuses.length" :description="detail.noShow.status==='WAITING_EXCUSE' ? '等待家属在 ' + fmt(detail.noShow.excuseDeadline) + ' 前提交说明' : '暂无说明'" :image-size="60" />
        <el-alert v-for="(ex,i) in detail.excuses" :key="i" class="mb8" type="warning" :closable="false" show-icon
                  :title="label('FamilyExcuseReason', ex.reason) + '（' + fmt(ex.submittedAt) + '）'"
                  :description="ex.content + (ex.expectedHandling ? '；家属期望：' + ex.expectedHandling : '')" />

        <template v-if="detail.bedPause">
          <div class="section-title mt-16">家庭病床暂停</div>
          <el-alert type="error" :closable="false" show-icon
                    :title="detail.bedPause.pauseNo + ' · ' + label('BedPauseStatus', detail.bedPause.status)"
                    :description="detail.bedPause.pauseNote" />
        </template>

        <template v-if="detail.noShow.decision">
          <div class="section-title mt-16">卫生院审核</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="决定">{{ label('NoShowDecision', detail.noShow.decision) }}</el-descriptions-item>
            <el-descriptions-item label="核定空跑费">¥{{ detail.noShow.approvedFee || 0 }}<span v-if="detail.noShow.deductServiceCount > 0">；扣除服务 {{ detail.noShow.deductServiceCount }} 次</span></el-descriptions-item>
            <el-descriptions-item v-if="detail.noShow.adjustedNextVisitAt" label="调整后下次巡诊">{{ fmt(detail.noShow.adjustedNextVisitAt) }}（{{ detail.noShow.adjustedVisit?.visitNo }}）</el-descriptions-item>
            <el-descriptions-item label="审核人/意见">{{ detail.noShow.reviewer?.name || '-' }}：{{ detail.noShow.reviewNote || '-' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
    </el-drawer>

    <!-- 家属补充说明 -->
    <el-dialog v-model="excuseVisible" title="家属补充说明（费用核定前）" width="520px">
      <el-alert type="info" :closable="false" class="mb12"
                title="请如实说明当天未能开门的原因（如临时急诊、手机没电、地址变更），并尽量提供佐证；卫生院将据此决定是否减免空跑费或调整巡诊。" />
      <el-form :model="excuseForm" label-width="92px">
        <el-form-item label="事由分类" required>
          <el-select v-model="excuseForm.reason" style="width:100%">
            <el-option v-for="(v,k) in ENUMS.FamilyExcuseReason" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="情况说明" required>
          <el-input v-model="excuseForm.content" type="textarea" :rows="4"
                    placeholder="如：老人突发胸闷，家属陪同去县医院急诊，走得急没来得及打电话……" />
        </el-form-item>
        <el-form-item label="佐证材料">
          <UploadPhotos v-model="excuseForm.evidencePaths" type="evidence" />
        </el-form-item>
        <el-form-item label="期望处理">
          <el-input v-model="excuseForm.expectedHandling" placeholder="如：请求减免空跑费 / 改约到本周末" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="excuseVisible=false">取消</el-button>
        <el-button type="primary" @click="submitExcuse">提交说明</el-button></template>
    </el-dialog>

    <!-- 卫生院审核 -->
    <el-dialog v-model="reviewVisible" title="卫生院审核空跑费用/改约" width="560px">
      <el-alert type="warning" :closable="false" class="mb12"
                :title="'说明截止 ' + fmt(reviewRow?.excuseDeadline) + '；家属已提交 ' + reviewExcuseCount + ' 份说明。收费/扣次须在家属说明期结束或家属已说明后办理。'" />
      <el-form :model="reviewForm" label-width="118px">
        <el-form-item label="处理决定" required>
          <el-radio-group v-model="reviewForm.decision">
            <el-radio v-for="(v,k) in ENUMS.NoShowDecision" :key="k" :value="k" :border="true" class="mb8">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="reviewForm.decision==='PARTIAL_FEE'" label="核定金额(元)" required>
          <el-input-number v-model="reviewForm.approvedFee" :min="0" :precision="2" :step="5" />
          <span class="muted"> 标准空跑费 ¥{{ standardFee }}</span>
        </el-form-item>
        <el-form-item v-if="reviewForm.decision==='DEDUCT_SERVICE'" label="扣除次数">
          <el-input-number v-model="reviewForm.deductServiceCount" :min="1" :max="5" />
        </el-form-item>
        <el-form-item v-if="reviewForm.decision==='ADJUST_NEXT'" label="下次巡诊时间" required>
          <el-date-picker v-model="reviewForm.nextVisitTime" type="datetime" style="width:100%" />
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="reviewForm.reviewNote" type="textarea" :rows="3"
                    placeholder="结合家属说明、门口照片、通话与等待记录给出结论" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewVisible=false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交审核</el-button></template>
    </el-dialog>

    <!-- 医生复评 -->
    <el-dialog v-model="reevalVisible" title="家庭医生重新评估" width="520px">
      <el-alert type="warning" :closable="false" class="mb12"
                :title="currentPause?.patient.name + ' 因“' + label('BedPauseReason', currentPause?.reason) + '”暂停：' + (currentPause?.pauseNote || '')" />
      <el-form :model="reevalForm" label-width="120px">
        <el-form-item label="复评意见" required>
          <el-input v-model="reevalForm.note" type="textarea" :rows="4"
                    placeholder="病情/出院情况、可否恢复巡诊、护理项目是否调整" />
        </el-form-item>
        <el-form-item label="建议首次上门">
          <el-date-picker v-model="reevalForm.proposedNextVisitAt" type="datetime" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="reevalVisible=false">取消</el-button>
        <el-button type="primary" @click="submitReevaluate">提交复评意见</el-button></template>
    </el-dialog>

    <!-- 复评恢复 -->
    <el-dialog v-model="resumeVisible" title="恢复家庭病床与巡诊" width="520px">
      <el-form :model="resumeForm" label-width="120px">
        <el-form-item v-if="currentPause?.reevaluationNote" label="复评意见">
          <div class="muted">{{ currentPause.reevaluationNote }}</div>
        </el-form-item>
        <el-form-item label="首次上门时间">
          <el-date-picker v-model="resumeForm.nextVisitAt" type="datetime" style="width:100%" />
        </el-form-item>
        <el-form-item label="恢复说明">
          <el-input v-model="resumeForm.note" type="textarea" :rows="3" placeholder="确认患者已出院/病情稳定，恢复计划巡诊" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="resumeVisible=false">取消</el-button>
        <el-button type="success" @click="submitResume">确认恢复巡诊</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from '../util/dayjs-lite'
import api from '../api'
import { useAuthStore } from '../store/auth'
import { ENUMS, label, tagType } from '../labels'
import UploadPhotos from '../components/UploadPhotos.vue'

const auth = useAuthStore()
const tab = ref('noshow')
const canPause = computed(() => ['ADMIN', 'FAMILY_DOCTOR', 'NURSE', 'FAMILY'].includes(auth.role))
const canReview = computed(() => auth.isAdmin || auth.isFinance)

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

// ===== 未遇列表 =====
const noShows = ref([])
const loading = ref(false)
const filter = ref('')
async function loadNoShows() {
  loading.value = true
  noShows.value = (await api.get('/no-show' + (filter.value ? '?status=' + filter.value : ''))).data
  loading.value = false
}
onMounted(loadNoShows)

function canExcuse(row) {
  return row.status !== 'REVIEWED' && row.status !== 'CLOSED'
}

// 详情
const detailVisible = ref(false)
const detail = ref(null)
async function viewNoShow(row) {
  detail.value = (await api.get(`/no-show/${row.id}`)).data
  detailVisible.value = true
}

// 家属说明
const excuseVisible = ref(false)
const excuseForm = reactive({ reason: 'EMERGENCY', content: '', evidencePaths: [], expectedHandling: '' })
let excuseTarget = null
function openExcuse(row) {
  excuseTarget = row
  Object.assign(excuseForm, { reason: 'EMERGENCY', content: '', evidencePaths: [], expectedHandling: '' })
  excuseVisible.value = true
}
async function submitExcuse() {
  if (!excuseForm.content) return ElMessage.warning('请填写情况说明')
  await api.post(`/no-show/${excuseTarget.id}/excuse`, {
    reason: excuseForm.reason,
    content: excuseForm.content,
    evidencePaths: excuseForm.evidencePaths,
    expectedHandling: excuseForm.expectedHandling
  })
  ElMessage.success('补充说明已提交，等待卫生院审核')
  excuseVisible.value = false
  loadNoShows()
}

// 卫生院审核
const reviewVisible = ref(false)
const reviewRow = ref(null)
const reviewForm = reactive({})
const reviewExcuseCount = ref(0)
const standardFee = ref('30')
async function openReview(row) {
  reviewRow.value = row
  const d = (await api.get(`/no-show/${row.id}`)).data
  reviewExcuseCount.value = d.excuses.length
  standardFee.value = d.standardFee ?? '30'
  Object.assign(reviewForm, {
    decision: d.excuses.length ? 'WAIVE' : 'CHARGE_FEE',
    approvedFee: Number(d.standardFee || 30),
    deductServiceCount: 1,
    nextVisitTime: null,
    reviewNote: ''
  })
  reviewVisible.value = true
}
async function submitReview() {
  const body = {
    decision: reviewForm.decision,
    reviewNote: reviewForm.reviewNote
  }
  if (reviewForm.decision === 'PARTIAL_FEE') body.approvedFee = reviewForm.approvedFee
  if (reviewForm.decision === 'DEDUCT_SERVICE') body.deductServiceCount = reviewForm.deductServiceCount
  if (reviewForm.decision === 'ADJUST_NEXT') {
    if (!reviewForm.nextVisitTime) return ElMessage.warning('请选择下一次巡诊时间')
    body.nextVisitTime = dayjs(reviewForm.nextVisitTime)
  }
  await api.post(`/no-show/${reviewRow.value.id}/review`, body)
  ElMessage.success('审核完成')
  reviewVisible.value = false
  loadNoShows()
}

// ===== 病床暂停 / 复评 =====
const pauses = ref([])
const pauseLoading = ref(false)
const pauseFilter = ref('PAUSED')
async function loadPauses() {
  pauseLoading.value = true
  pauses.value = (await api.get('/no-show/pauses' + (pauseFilter.value ? '?status=' + pauseFilter.value : ''))).data
  pauseLoading.value = false
}
onMounted(loadPauses)

const reevalVisible = ref(false)
const resumeVisible = ref(false)
const currentPause = ref(null)
const reevalForm = reactive({})
const resumeForm = reactive({})
function openReevaluate(row) {
  currentPause.value = row
  Object.assign(reevalForm, { note: '', proposedNextVisitAt: null })
  reevalVisible.value = true
}
async function submitReevaluate() {
  if (!reevalForm.note) return ElMessage.warning('请填写复评意见')
  const body = { note: reevalForm.note }
  if (reevalForm.proposedNextVisitAt) body.proposedNextVisitAt = dayjs(reevalForm.proposedNextVisitAt)
  await api.post(`/no-show/pauses/${currentPause.value.id}/reevaluate`, body)
  ElMessage.success('复评意见已保存，可在复评通过后恢复巡诊')
  reevalVisible.value = false
  loadPauses()
}
function openResume(row) {
  currentPause.value = row
  Object.assign(resumeForm, { note: '', nextVisitAt: row.nextVisitAt ? new Date(row.nextVisitAt) : null })
  resumeVisible.value = true
}
async function submitResume() {
  const body = { note: resumeForm.note || '复评通过，恢复巡诊' }
  if (resumeForm.nextVisitAt) body.nextVisitAt = dayjs(resumeForm.nextVisitAt)
  await api.post(`/no-show/pauses/${currentPause.value.id}/resume`, body)
  ElMessage.success('家庭病床与巡诊已恢复')
  resumeVisible.value = false
  loadPauses()
}
</script>

<style scoped>
.head-line { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.mb12 { margin-bottom: 12px; } .mb8 { margin-bottom: 8px; }
.mt-16 { margin-top: 16px; }
.section-title { font-weight: 600; margin-bottom: 8px; }
.door-thumb { width: 150px; height: 100px; border-radius: 6px; margin: 4px 8px 4px 0; border: 1px solid #ddd; }
.fee { color: #e6a23c; font-weight: 600; margin-left: 6px; }
</style>
