<template>
  <div class="schedule-page" v-loading="loading">
    <!-- 顶部控制栏 -->
    <el-card class="control-card">
      <div class="control-bar">
        <div class="week-nav">
          <el-button-group>
            <el-button :icon="ArrowLeft" @click="prevWeek" :disabled="currentWeek <= 1">上一周</el-button>
            <el-button @click="jumpToCurrentWeek">本周</el-button>
            <el-button @click="nextWeek" :disabled="currentWeek >= maxWeek">下一周</el-button>
          </el-button-group>
          <el-select v-model="currentWeek" @change="changeWeek" class="week-select" placeholder="选择周">
            <el-option v-for="w in maxWeek" :key="w" :label="`第 ${w} 周`" :value="w" />
          </el-select>
        </div>

        <div class="semester-info" v-if="scheduleData">
          <span class="week-label">
            第 <strong>{{ scheduleData.curriculum.currentWeek }}</strong> / {{ scheduleData.curriculum.maxWeek }} 周
          </span>
          <span class="week-range" v-if="weekRangeText">{{ weekRangeText }}</span>
        </div>

        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="grid"><el-icon><Grid /></el-icon> 表格</el-radio-button>
          <el-radio-button value="list"><el-icon><List /></el-icon> 列表</el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <!-- 表格视图（CSS Grid） -->
    <el-card v-if="viewMode === 'grid' && scheduleData" class="table-card">
      <div class="schedule-grid" :style="gridStyle">
        <!-- 表头行 -->
        <div class="grid-cell grid-header time-head">时间</div>
        <div
          v-for="d in weekdays"
          :key="'h-' + d.key"
          class="grid-cell grid-header"
          :class="{ 'today-col': d.key === todayDow }"
        >
          {{ d.label }}<br /><span class="day-date">{{ d.dateStr }}</span>
        </div>

        <!-- 时间标签（第1列） -->
        <template v-for="(slot, p) in timeSlots" :key="'t-' + p">
          <div
            class="grid-cell time-cell"
            :style="{ gridRow: p + 2, gridColumn: '1' }"
          >
            <span class="period-num">{{ p + 1 }}</span>
            <span class="period-time">{{ slot }}</span>
          </div>
        </template>

        <!-- 课程块（CSS Grid 定位） -->
        <div
          v-for="cell in gridCells"
          :key="'c-' + cell.lesson.id + '-' + cell.day + '-' + cell.period"
          class="lesson-block"
          :style="{
            gridRow: `${cell.rowStart} / ${cell.rowEnd}`,
            gridColumn: cell.col,
            backgroundColor: cell.color,
          }"
        >
          <div class="lesson-name">{{ cell.lesson.name }}</div>
          <div class="lesson-detail">{{ cell.lesson.teacherName || '' }}</div>
          <div class="lesson-detail" v-if="cell.lesson.location">
            <el-icon><Location /></el-icon>{{ cell.lesson.location }}
          </div>
        </div>
      </div>
    </el-card>

    <!-- 列表视图 -->
    <el-card v-if="viewMode === 'list' && scheduleData" class="list-card">
      <el-empty v-if="sortedLessons.length === 0" description="本周暂无课程安排" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="(lesson, idx) in sortedLessons"
          :key="idx"
          :timestamp="`周${weekdayLabel(lesson.dayOfWeek)} · 第${lesson.beginNumber}-${lesson.beginNumber + lesson.length - 1}节`"
          placement="top"
          :color="colors[idx % colors.length]"
        >
          <el-card shadow="hover" class="lesson-timeline-card">
            <h4>{{ lesson.name }}</h4>
            <div class="timeline-info">
              <span><el-icon><User /></el-icon> {{ lesson.teacherName || '未知教师' }}</span>
              <span v-if="lesson.className"><el-icon><School /></el-icon> {{ lesson.className }}</span>
              <span v-if="lesson.location"><el-icon><Location /></el-icon> {{ lesson.location }}</span>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 无数据 -->
    <el-card v-if="!scheduleData && !loading">
      <el-empty description="暂无课程表数据，请先确保已登录且 Cookie 有效" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ArrowLeft, Grid, List, Location, User, School } from '@element-plus/icons-vue'
import { courseApi } from '@/api/course'
import type { ScheduleData, LessonItem } from '@/types'

const loading = ref(false)
const scheduleData = ref<ScheduleData | null>(null)
const currentWeek = ref(1)
const maxWeek = ref(18)
const viewMode = ref<'grid' | 'list'>('grid')
const initialLoad = ref(true)
const todayDow = new Date().getDay() || 7 // 1=Mon .. 6=Sat, 7=Sun

const weekdays = [
  { key: 1, label: '周一', dateStr: '' },
  { key: 2, label: '周二', dateStr: '' },
  { key: 3, label: '周三', dateStr: '' },
  { key: 4, label: '周四', dateStr: '' },
  { key: 5, label: '周五', dateStr: '' },
]

const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#B37FEB', '#36CFC9']

const timeSlots = computed(() => scheduleData.value?.curriculum?.lessonTimeConfigArray || [])

/** CSS Grid 动态样式：列数 = 1(时间) + 5(周一~五) */
const gridStyle = computed(() => ({
  gridTemplateColumns: `100px repeat(5, 1fr)`,
  gridTemplateRows: `auto repeat(${timeSlots.value.length}, minmax(60px, auto))`,
}))

/** 周日期范围文本 */
const weekRangeText = computed(() => {
  const data = scheduleData.value
  if (!data?.curriculum) return ''
  const fw = data.curriculum.firstWeekDateReal
  if (!fw) return ''
  const offset = (currentWeek.value - 1) * 7 * 86400000
  const mon = new Date(fw + offset)
  const sun = new Date(mon.getTime() + 6 * 86400000)
  return `${fmt(mon)} ~ ${fmt(sun)}`
})

/** 更新 weekday 日期显示 */
function updateWeekdayDates() {
  const data = scheduleData.value
  if (!data?.curriculum?.firstWeekDateReal) return
  const offset = (currentWeek.value - 1) * 7 * 86400000
  weekdays.forEach((d, i) => {
    const date = new Date(data.curriculum!.firstWeekDateReal + offset + i * 86400000)
    d.dateStr = `${date.getMonth() + 1}/${date.getDate()}`
  })
}

/** 当前周课程 */
const currentWeekLessons = computed(() => {
  const raw = scheduleData.value?.lessonArray || []
  return raw.filter(
    (l) => !l.weeks || l.weeks.length === 0 || l.weeks.includes(currentWeek.value)
  )
})

/** 列表视图排序 */
const sortedLessons = computed(() =>
  [...currentWeekLessons.value].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.beginNumber - b.beginNumber)
)

/** CSS Grid 课程块预计算 */
interface GridCell {
  lesson: LessonItem
  day: number
  period: number
  rowStart: number
  rowEnd: number
  col: number
  color: string
}

const gridCells = computed<GridCell[]>(() => {
  const cells: GridCell[] = []
  const sorted = [...currentWeekLessons.value].sort(
    (a, b) => a.dayOfWeek - b.dayOfWeek || a.beginNumber - b.beginNumber
  )
  sorted.forEach((l, idx) => {
    cells.push({
      lesson: l,
      day: l.dayOfWeek,
      period: l.beginNumber,
      rowStart: l.beginNumber + 1,   // +1 因为第1行是表头
      rowEnd: l.beginNumber + l.length + 1,
      col: l.dayOfWeek + 1,           // +1 因为第1列是时间标签
      color: colors[idx % colors.length],
    })
  })
  return cells
})

function weekdayLabel(dow: number) {
  return ['一', '二', '三', '四', '五', '六', '日'][dow - 1] || '?'
}

function fmt(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function fetchSchedule() {
  loading.value = true
  try {
    // 首次加载不传 week，让后端按默认当前周返回数据
    const weekParam = initialLoad.value ? undefined : currentWeek.value
    const res = await courseApi.getSchedule(weekParam)
    scheduleData.value = res.data.data
    if (scheduleData.value?.curriculum) {
      maxWeek.value = scheduleData.value.curriculum.maxWeek || 18
      if (initialLoad.value) {
        currentWeek.value = scheduleData.value.curriculum.currentWeek || 1
        initialLoad.value = false
      }
    }
    updateWeekdayDates()
  } catch {
    scheduleData.value = null
  } finally {
    loading.value = false
  }
}

function prevWeek() {
  if (currentWeek.value > 1) {
    currentWeek.value--
    fetchSchedule()
  }
}
function nextWeek() {
  if (currentWeek.value < maxWeek.value) {
    currentWeek.value++
    fetchSchedule()
  }
}
function jumpToCurrentWeek() {
  if (scheduleData.value?.curriculum?.currentWeek) {
    currentWeek.value = scheduleData.value.curriculum.currentWeek
    fetchSchedule()
  }
}
function changeWeek() {
  fetchSchedule()
}

onMounted(() => fetchSchedule())
</script>

<style scoped>
.control-card { margin-bottom: 16px; }
.control-bar {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 12px;
}
.week-nav { display: flex; align-items: center; gap: 8px; }
.week-select { width: 140px; }
.semester-info {
  display: flex; align-items: center; gap: 12px;
  color: #606266; font-size: 14px;
}
.week-label strong { color: #409EFF; font-size: 18px; }
.week-range { color: #909399; font-size: 13px; }

/* ===== CSS Grid 表格 ===== */
.table-card :deep(.el-card__body) { padding: 0; overflow-x: auto; }

.schedule-grid {
  display: grid;
  min-width: 700px;
  border-collapse: collapse;
}

.grid-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px 4px;
  border-right: 1px solid #dcdfe6;
  border-bottom: 1px solid #ebeef5;
  min-height: 60px;
  position: relative;
}

.grid-header {
  background: #f5f7fa;
  border-bottom: 2px solid #dcdfe6;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  position: sticky;
  top: 0;
  z-index: 2;
}
.today-col {
  background: #ecf5ff;
}

.time-head {
  color: #909399;
  font-size: 13px;
}
.time-cell {
  color: #606266;
}
.day-date {
  font-weight: 400;
  font-size: 11px;
  color: #909399;
}
.period-num {
  font-size: 15px;
  font-weight: 700;
  color: #409EFF;
}
.period-time {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.lesson-block {
  border-radius: 6px;
  padding: 6px 8px;
  color: #fff;
  overflow: hidden;
  z-index: 1;
  box-shadow: 0 1px 4px rgba(0,0,0,0.12);
  margin: 2px 3px;
  transition: transform 0.15s;
  cursor: default;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: flex-start;
}
.lesson-block:hover {
  transform: scale(1.02);
  z-index: 3;
}
.lesson-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  margin-bottom: 2px;
}
.lesson-detail {
  font-size: 11px;
  opacity: 0.9;
  display: flex;
  align-items: center;
  gap: 2px;
  line-height: 1.4;
}

/* 列表 */
.list-card { max-width: 800px; }
.lesson-timeline-card h4 { margin: 0 0 8px 0; font-size: 15px; color: #303133; }
.timeline-info {
  display: flex; flex-wrap: wrap; gap: 12px;
  font-size: 13px; color: #606266;
}
.timeline-info span { display: flex; align-items: center; gap: 4px; }

/* ===== 移动端 ===== */
@media (max-width: 767px) {
  .control-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .week-nav {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .week-nav .el-button-group {
    display: flex;
    width: 100%;
  }

  .week-nav .el-button-group .el-button {
    flex: 1;
    font-size: 13px;
  }

  .week-select {
    width: 100%;
  }

  .semester-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .control-bar .el-radio-group {
    align-self: center;
  }

  .list-card {
    max-width: 100%;
  }

  .schedule-grid {
    min-width: 500px;
  }

  .grid-cell {
    padding: 4px 2px;
    min-height: 44px;
  }

  .grid-header {
    font-size: 12px;
  }

  .lesson-name {
    font-size: 11px;
  }

  .lesson-detail {
    font-size: 10px;
  }

  .lesson-block {
    padding: 4px 6px;
    margin: 1px;
    border-radius: 4px;
  }
}
</style>
