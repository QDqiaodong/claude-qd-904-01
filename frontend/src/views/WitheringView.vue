<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>摊青槽失水监控</h2>
      <span class="tf-sub">
        一排摊青槽 · 失水率达目标即可放行下槽 · 下槽后槽号自动释放，可承接下一批鲜叶；每轮摊青独立留痕
      </span>
    </div>

    <!-- ============ 开槽登记 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        开槽登记
        <el-button link type="primary" size="small" @click="showOpen = !showOpen">
          {{ showOpen ? '收起' : '登记一槽鲜叶' }}
        </el-button>
      </div>
      <el-form v-if="showOpen" label-width="92px" size="small" inline class="wt-open">
        <el-form-item label="槽号">
          <el-input v-model="openForm.troughNo" placeholder="如 TQ-09" style="width: 120px" />
        </el-form-item>
        <el-form-item label="茶类">
          <el-select v-model="openForm.teaType" style="width: 120px">
            <el-option v-for="t in teaTypeList" :key="t" :label="teaName(t)" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="摊青方式">
          <el-select v-model="openForm.witherMethod" style="width: 130px">
            <el-option v-for="m in witherMethods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="摊叶厚度 cm">
          <el-input-number v-model="openForm.leafThicknessCm" :min="1" :max="15" :step="0.5" :precision="1" style="width: 120px" />
        </el-form-item>
        <el-form-item label="在槽 kg">
          <el-input-number v-model="openForm.leafWeightKg" :min="1" :max="9999" :step="10" :precision="2" style="width: 130px" />
        </el-form-item>
        <el-form-item label="进厂单号">
          <el-select v-model="openForm.intakeNo" clearable filterable style="width: 170px">
            <el-option v-for="r in intakes" :key="r.intakeNo" :label="r.intakeNo" :value="r.intakeNo" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" :loading="opening" @click="openTrough">开槽</el-button>
        </el-form-item>
      </el-form>
      <div class="wt-reuse-hint">
        槽号口径：<b>摊青中 / 可杀青</b> 的槽号被当前批次占用，不能重复开槽；<b>已下槽</b>
        的槽号视为空槽，可直接登记新一批鲜叶（自动计为该槽号的下一轮），上一轮记录在下方历史区可追溯。
      </div>
    </section>

    <!-- ============ 当前占用摊青槽 ============ -->
    <div class="wt-section-head">
      <span class="wt-section-title">当前占用（{{ active.length }} 槽）</span>
      <small>仅这里的槽位计入「在槽 / 达标槽数」</small>
    </div>
    <div class="wt-rail">
      <div
        v-for="t in active"
        :key="t.troughNo + '#r' + t.roundNo"
        class="wt-card"
        :class="{ 'is-ready': t.status === 'READY', 'is-selected': isSelected(t) }"
        @click="selectCard(t)"
      >
        <div class="wt-card-head">
          <b>{{ t.troughNo }}</b>
          <el-tag size="small" :type="t.status === 'READY' ? 'success' : 'warning'">
            {{ statusName(t.status) }}
          </el-tag>
        </div>
        <div class="wt-card-sub">
          {{ teaName(t.teaType) }} · {{ t.witherMethod || '-' }} · 第 {{ t.roundNo }} 轮
          <el-tag v-if="roundsOf(t.troughNo) > 1" size="small" type="info" effect="plain" class="wt-round-tag">
            槽位复用 · 累计 {{ roundsOf(t.troughNo) }} 轮
          </el-tag>
        </div>

        <!-- 叶片铺层 SVG -->
        <svg class="wt-layer" viewBox="0 0 160 66" preserveAspectRatio="none">
          <path d="M8 58 L152 58 L146 64 L14 64 Z" fill="#c4b99f" />
          <line x1="6" y1="58" x2="154" y2="58" stroke="#8a8570" stroke-width="1" />
          <g v-for="(leaf, i) in layer(t)" :key="i">
            <ellipse
              :cx="leaf.cx"
              :cy="leaf.cy"
              :rx="leaf.rx"
              :ry="leaf.ry"
              :transform="`rotate(${leaf.rot} ${leaf.cx} ${leaf.cy})`"
              :fill="leaf.fill"
              stroke="#33691e"
              stroke-width="0.5"
            />
          </g>
        </svg>

        <div class="wt-metrics">
          <span>厚度 {{ num(t.leafThicknessCm, 1) }} cm</span>
          <span>在槽 {{ num(t.leafWeightKg, 0) }} kg</span>
        </div>

        <div class="wt-bar">
          <div class="wt-bar-fill" :style="{ width: progress(t) + '%' }"></div>
          <div class="wt-bar-target"></div>
        </div>
        <div class="wt-bar-label">
          失水 <b :class="{ 'tf-green': t.status === 'READY' }">{{ num(t.currentLossPct, 2) }}%</b>
          / 目标 {{ num(t.targetLossPct, 2) }}%
        </div>

        <div v-if="t.status === 'READY'" class="wt-ready">可杀青</div>
        <div v-else class="wt-need">还差 {{ num(gap(t), 2) }}%</div>
      </div>
      <div v-if="!active.length" class="tf-empty">当前没有在槽摊青的批次，所有槽号均可开槽</div>
    </div>

    <!-- ============ 选中轮次：登记 / 放行 ============ -->
    <section v-if="current" class="tf-card wt-detail">
      <div class="tf-card-title">
        {{ current.troughNo }} 环境登记
        <small>
          第 {{ current.roundNo }} 轮 · {{ teaName(current.teaType) }} · {{ statusName(current.status) }}
          · 开槽 {{ fmtTime(current.createdAt) }} · 更新于 {{ fmtTime(current.updatedAt) }}
        </small>
      </div>

      <div class="wt-detail-body">
        <div class="wt-gauge">
          <svg viewBox="0 0 180 180" class="wt-gauge-svg">
            <circle cx="90" cy="90" r="70" fill="#faf7f0" stroke="#ddd5c2" />
            <circle
              cx="90"
              cy="90"
              r="70"
              fill="none"
              stroke="#33691e"
              stroke-width="8"
              stroke-linecap="round"
              :stroke-dasharray="String(ringDash) + ' 999'"
              transform="rotate(-90 90 90)"
            />
            <text x="90" y="84" class="wt-gauge-num">{{ num(current.currentLossPct, 1) }}%</text>
            <text x="90" y="104" class="wt-gauge-cap">当前失水率</text>
            <text x="90" y="124" class="wt-gauge-cap2">目标 {{ num(current.targetLossPct, 1) }}%</text>
          </svg>
        </div>

        <el-form label-width="86px" size="small" class="wt-form">
          <el-form-item label="环境温度 ℃">
            <el-input-number v-model="logForm.tempC" :min="0" :max="60" :step="0.5" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item label="相对湿度 %">
            <el-input-number v-model="logForm.humidityPct" :min="0" :max="100" :step="1" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item :label="'失水率 ' + num(logForm.currentLossPct, 1) + '%'">
            <el-slider v-model="logForm.currentLossPct" :min="0" :max="Math.max(Number(current.targetLossPct) + 10, 40)" :step="0.1" style="width: 220px" />
          </el-form-item>
          <el-form-item label="摊青时长 h">
            <el-input-number v-model="logForm.witherHours" :min="0" :max="72" :step="0.5" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item label="摊叶厚度 cm">
            <el-input-number v-model="logForm.leafThicknessCm" :min="1" :max="15" :step="0.5" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" :loading="logging" @click="saveLog">
              登记温湿度与失水率
            </el-button>
            <el-button
              size="small"
              type="success"
              :loading="releasing"
              :disabled="current.status === 'DONE'"
              @click="releaseCard"
            >
              放行下槽
            </el-button>
            <span class="wt-hint">
              失水率未达 {{ num(current.targetLossPct, 2) }}% 时，后端会拒绝放行；下槽后本槽号即可开新批次
            </span>
          </el-form-item>
        </el-form>
      </div>
    </section>

    <!-- ============ 历史完成履历 ============ -->
    <section class="tf-card wt-history">
      <div class="tf-card-title">
        历史完成履历（{{ history.length }} 轮）
        <el-button link type="primary" size="small" @click="showHistory = !showHistory">
          {{ showHistory ? '收起' : '查看已下槽记录' }}
        </el-button>
      </div>
      <el-table v-if="showHistory" :data="history" size="small" stripe class="wt-history-table"
                @row-click="selectHistory" :row-class-name="historyRowClass" highlight-current-row>
        <el-table-column label="槽号" width="90">
          <template #default="{ row }">
            <b>{{ row.troughNo }}</b>
            <el-tag size="small" type="info" effect="plain" class="wt-round-tag">第 {{ row.roundNo }} 轮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="茶类" width="90">
          <template #default="{ row }">{{ teaName(row.teaType) }}</template>
        </el-table-column>
        <el-table-column prop="witherMethod" label="摊青方式" width="110" />
        <el-table-column label="进厂单 / 批次" min-width="220">
          <template #default="{ row }">
            <span>{{ row.intakeNo || '-' }}</span>
            <span v-if="row.batchNo"> / {{ row.batchNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在槽 kg" width="100">
          <template #default="{ row }">{{ num(row.leafWeightKg, 0) }}</template>
        </el-table-column>
        <el-table-column label="失水率/目标" width="130">
          <template #default="{ row }">
            <span class="tf-green"><b>{{ num(row.currentLossPct, 2) }}%</b></span>
            / {{ num(row.targetLossPct, 2) }}%
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开槽时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="下槽时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.releasedAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="showHistory && !history.length" class="tf-empty">还没有已下槽的摊青履历</div>
    </section>

    <!-- ============ 历史轮次只读详情（可追溯） ============ -->
    <section v-if="historyCurrent" class="tf-card wt-detail wt-history-detail">
      <div class="tf-card-title">
        {{ historyCurrent.troughNo }} 第 {{ historyCurrent.roundNo }} 轮摊青履历（只读）
        <small>
          {{ teaName(historyCurrent.teaType) }} · {{ statusName(historyCurrent.status) }}
          · 开槽 {{ fmtTime(historyCurrent.createdAt) }} · 下槽 {{ fmtTime(historyCurrent.releasedAt) }}
          <el-button link type="primary" size="small" @click="historyCurrent = null">关闭</el-button>
        </small>
      </div>
      <div class="wt-history-body">
        <div class="wt-history-gauge">
          <span class="wt-history-loss">{{ num(historyCurrent.currentLossPct, 2) }}%</span>
          <small>下槽失水率 / 目标 {{ num(historyCurrent.targetLossPct, 2) }}%</small>
        </div>
        <el-descriptions :column="3" size="small" border class="wt-history-desc">
          <el-descriptions-item label="摊青方式">{{ historyCurrent.witherMethod || '-' }}</el-descriptions-item>
          <el-descriptions-item label="摊叶厚度">{{ num(historyCurrent.leafThicknessCm, 1) }} cm</el-descriptions-item>
          <el-descriptions-item label="摊青时长">{{ num(historyCurrent.witherHours, 1) }} h</el-descriptions-item>
          <el-descriptions-item label="环境温度">{{ num(historyCurrent.tempC, 1) }} ℃</el-descriptions-item>
          <el-descriptions-item label="相对湿度">{{ num(historyCurrent.humidityPct, 1) }} %</el-descriptions-item>
          <el-descriptions-item label="在槽鲜叶">{{ num(historyCurrent.leafWeightKg, 0) }} kg</el-descriptions-item>
          <el-descriptions-item label="进厂单号">{{ historyCurrent.intakeNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工单批次">{{ historyCurrent.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="该槽累计轮次">{{ roundsOf(historyCurrent.troughNo) }} 轮</el-descriptions-item>
        </el-descriptions>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { intakeApi, witheringApi } from '../api'

const TEA_TYPES = ['GREEN', 'RED', 'OOLONG', 'WHITE', 'PUER']
const TEA_NAMES = { GREEN: '绿茶', RED: '红茶', OOLONG: '乌龙茶', WHITE: '白茶', PUER: '普洱茶' }
const WITHER_METHODS = ['日光晒青', '室内萎凋']
const teaTypeList = TEA_TYPES
const witherMethods = WITHER_METHODS

const props = defineProps({
  teaType: { type: String, default: 'GREEN' }
})

const active = ref([])
const history = ref([])
const intakes = ref([])
/** 选中项：{ kind: 'active' | 'history', id } */
const selected = ref(null)
const historyCurrent = ref(null)
const showOpen = ref(false)
const showHistory = ref(true)
const opening = ref(false)
const logging = ref(false)
const releasing = ref(false)
/** 首次加载是否已完成（避免刷新后抢占用户选中的历史履历） */
const initialized = ref(false)

const openForm = reactive({
  troughNo: '',
  teaType: props.teaType || 'GREEN',
  witherMethod: '室内萎凋',
  leafThicknessCm: 3,
  leafWeightKg: 180,
  intakeNo: ''
})

const logForm = reactive({
  tempC: 24,
  humidityPct: 68,
  currentLossPct: 0,
  witherHours: 6,
  leafThicknessCm: 3
})

const teaName = (t) => TEA_NAMES[t] || t || '-'
const num = (v, d = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(d))
const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')
const statusName = (s) => ({ WITHERING: '摊青中', READY: '可杀青', DONE: '已下槽' }[s] || s || '-')

/** 该槽号累计摊青轮数（含当前轮与历史轮），>1 即发生过槽位复用 */
function roundsOf(troughNo) {
  return allRecords.value.filter((r) => r.troughNo === troughNo).length
}

const allRecords = computed(() => [...active.value, ...history.value])

const current = computed(() => {
  if (!selected.value || selected.value.kind !== 'active') return null
  return active.value.find((t) => t.id === selected.value.id) || null
})

function isSelected(t) {
  return selected.value && selected.value.kind === 'active' && selected.value.id === t.id
}

/** 失水完成百分比 */
function progress(t) {
  const cur = Number(t.currentLossPct || 0)
  const target = Number(t.targetLossPct || 0)
  if (!target) return 0
  return Math.max(0, Math.min(100, (cur / target) * 100))
}

function gap(t) {
  return Math.max(0, Number(t.targetLossPct || 0) - Number(t.currentLossPct || 0))
}

/** 槽内层叶片：数量随摊叶厚度变化 */
function layer(t) {
  const thickness = Number(t.leafThicknessCm || 3)
  const count = Math.max(4, Math.min(26, Math.round(thickness * 4)))
  const rowsNum = Math.max(1, Math.min(4, Math.round(thickness / 4) + 1))
  const list = []
  for (let i = 0; i < count; i++) {
    const row = i % rowsNum
    const col = Math.floor(i / rowsNum)
    list.push({
      cx: 12 + col * 22 + (row % 2) * 9,
      cy: 54 - row * 9,
      rx: 8,
      ry: 3.4,
      rot: (i % 2 === 0 ? -1 : 1) * (12 + (i % 6) * 11),
      fill: i % 4 === 0 ? '#7ea55f' : i % 4 === 1 ? '#5b8b3f' : i % 4 === 2 ? '#a6c088' : '#8fa870'
    })
  }
  return list
}

const ringDash = computed(() => {
  const p = current.value ? progress(current.value) : 0
  return (p / 100) * 2 * Math.PI * 70
})

async function load() {
  try {
    const res = await witheringApi.board()
    const data = res && res.data ? res.data : {}
    active.value = data.active || []
    history.value = data.history || []
    /* 选中项刷新后仍存在则同步表单；当前轮已下槽则转去高亮其历史履历，不抢占选中 */
    if (selected.value && selected.value.kind === 'active') {
      const stillActive = active.value.find((t) => t.id === selected.value.id)
      if (stillActive) {
        fillLog(stillActive)
      } else {
        const becameHistory = history.value.find((r) => r.id === selected.value.id)
        selected.value = null
        if (becameHistory) {
          showHistory.value = true
          selectHistory(becameHistory)
        }
      }
    }
    /* 仅首次进入（无任何选中）时默认选中第一张在槽卡片 */
    if (!selected.value && !historyCurrent.value && active.value.length && !initialized.value) {
      selectCard(active.value[0])
    }
    initialized.value = true
  } catch (e) {
    ElMessage.error('加载摊青槽失败：' + e.message)
  }
}

async function loadIntakes() {
  try {
    const res = await intakeApi.all()
    intakes.value = res && res.data ? res.data : []
  } catch (e) {
    /* 关联进厂单列表加载失败不影响主流程 */
  }
}

function selectCard(t) {
  selected.value = { kind: 'active', id: t.id }
  historyCurrent.value = null
  fillLog(t)
}

function selectHistory(row) {
  historyCurrent.value = row
}

function historyRowClass({ row }) {
  return historyCurrent.value && historyCurrent.value.id === row.id ? 'wt-history-current-row' : ''
}

function fillLog(t) {
  logForm.tempC = Number(t.tempC || 0)
  logForm.humidityPct = Number(t.humidityPct || 0)
  logForm.currentLossPct = Number(t.currentLossPct || 0)
  logForm.witherHours = Number(t.witherHours || 0)
  logForm.leafThicknessCm = Number(t.leafThicknessCm || 3)
}

async function openTrough() {
  if (!openForm.troughNo || !String(openForm.troughNo).trim()) {
    ElMessage.warning('请填写槽号')
    return
  }
  opening.value = true
  try {
    const res = await witheringApi.open({
      troughNo: String(openForm.troughNo).trim(),
      teaType: openForm.teaType,
      witherMethod: openForm.witherMethod,
      leafThicknessCm: openForm.leafThicknessCm,
      leafWeightKg: openForm.leafWeightKg,
      intakeNo: openForm.intakeNo || undefined
    })
    const opened = res && res.data ? res.data : null
    ElMessage.success(res && res.message ? res.message : `已开槽 ${openForm.troughNo.trim()}`)
    showOpen.value = false
    openForm.troughNo = ''
    await load()
    if (opened) {
      const hit = active.value.find((t) => t.id === opened.id)
      if (hit) selectCard(hit)
    }
  } catch (e) {
    /* 进行中槽号重复开槽 / 并发占用：后端返回明确中文原因，刷新后只保留一个当前批次 */
    ElMessage.error(e.message || '开槽失败')
    await load()
  } finally {
    opening.value = false
  }
}

async function saveLog() {
  if (!current.value) return
  logging.value = true
  try {
    const res = await witheringApi.log(current.value.troughNo, {
      tempC: logForm.tempC,
      humidityPct: logForm.humidityPct,
      currentLossPct: logForm.currentLossPct,
      witherHours: logForm.witherHours,
      leafThicknessCm: logForm.leafThicknessCm
    })
    ElMessage.success(res && res.message ? res.message : '登记成功')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '登记失败')
  } finally {
    logging.value = false
  }
}

async function releaseCard() {
  if (!current.value) return
  releasing.value = true
  try {
    const res = await witheringApi.release(current.value.troughNo)
    ElMessage.success(res && res.message ? res.message : '已下槽')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '放行失败')
    /* 可能是另一人已先下槽：刷新对齐到唯一的当前状态 */
    await load()
  } finally {
    releasing.value = false
  }
}

watch(
  () => props.teaType,
  (v) => {
    openForm.teaType = v || 'GREEN'
  }
)

onMounted(() => {
  load()
  loadIntakes()
})
</script>

<style scoped>
.wt-open {
  display: flex;
  flex-wrap: wrap;
}

.wt-reuse-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #5d6353;
  background: #f4efe4;
  border-left: 3px solid #5b8b3f;
  border-radius: 3px;
  padding: 7px 10px;
  line-height: 1.7;
}

.wt-section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 14px 2px 8px;
}

.wt-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #2f3327;
}

.wt-section-head small {
  font-size: 11px;
  color: #8a8570;
}

.wt-rail {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 4px 2px 12px;
}

.wt-card {
  flex: 0 0 210px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #ddd5c2;
  border-radius: 6px;
  padding: 10px 12px 12px;
  cursor: pointer;
  transition: all 0.22s ease;
}

.wt-card:hover {
  border-color: #5b8b3f;
  box-shadow: 0 3px 12px rgba(51, 105, 30, 0.14);
}

.wt-card.is-selected {
  border-color: #33691e;
  box-shadow: 0 0 0 2px rgba(51, 105, 30, 0.18);
}

.wt-card.is-ready {
  background: linear-gradient(180deg, #eef5e6 0%, #ffffff 60%);
  border-color: #33691e;
}

.wt-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wt-card-head b {
  font-size: 15px;
  color: #33691e;
  letter-spacing: 1px;
}

.wt-card-sub {
  font-size: 11px;
  color: #5d6353;
  margin: 2px 0 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.wt-round-tag {
  transform: scale(0.92);
  transform-origin: left center;
}

.wt-layer {
  width: 100%;
  height: 66px;
  display: block;
  border-bottom: 1px solid #ddd5c2;
}

.wt-metrics {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #5d6353;
  margin: 6px 0 5px;
}

.wt-bar {
  position: relative;
  height: 9px;
  border: 1px solid #c4b99f;
  border-radius: 5px;
  background: #f4efe4;
  overflow: hidden;
}

.wt-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #7ea55f 0%, #33691e 100%);
  transition: width 0.5s ease;
}

.wt-bar-target {
  position: absolute;
  right: 0;
  top: 0;
  width: 2px;
  height: 100%;
  background: #b45309;
}

.wt-bar-label {
  font-size: 11px;
  color: #5d6353;
  margin-top: 5px;
}

.wt-ready {
  margin-top: 6px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #33691e;
  border-radius: 4px;
  padding: 3px 0;
  letter-spacing: 2px;
}

.wt-need {
  margin-top: 6px;
  text-align: center;
  font-size: 11px;
  color: #b45309;
}

.wt-detail-body {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.wt-gauge-svg {
  width: 180px;
  height: 180px;
}

.wt-gauge-num {
  font-size: 24px;
  font-weight: 700;
  fill: #33691e;
  text-anchor: middle;
}

.wt-gauge-cap {
  font-size: 11px;
  fill: #5d6353;
  text-anchor: middle;
}

.wt-gauge-cap2 {
  font-size: 11px;
  fill: #b45309;
  text-anchor: middle;
}

.wt-form {
  flex: 1 1 380px;
  min-width: 320px;
}

.wt-hint {
  margin-left: 10px;
  font-size: 11px;
  color: #5d6353;
}

.wt-history {
  margin-top: 14px;
}

.wt-history-table {
  margin-top: 8px;
  cursor: pointer;
}

.wt-history-table :deep(.wt-history-current-row) {
  background-color: #eef5e6 !important;
}

.wt-history-detail {
  margin-top: 12px;
  border-color: #c4b99f;
}

.wt-history-body {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  align-items: stretch;
}

.wt-history-gauge {
  flex: 0 0 170px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f4efe4;
  border: 1px solid #ddd5c2;
  border-radius: 6px;
  padding: 14px;
  text-align: center;
}

.wt-history-loss {
  font-size: 30px;
  font-weight: 700;
  color: #33691e;
  line-height: 1.2;
}

.wt-history-gauge small {
  font-size: 11px;
  color: #5d6353;
  margin-top: 6px;
}

.wt-history-desc {
  flex: 1 1 420px;
}
</style>
