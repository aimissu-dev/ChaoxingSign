<template>
  <div class="sign-config-page">
    <el-card>
      <template #header>
        <div class="page-header">
          <span><el-icon><Setting /></el-icon> 签到配置</span>
          <el-button type="primary" @click="openAddDialog">添加配置</el-button>
        </div>
      </template>

      <!-- 配置列表表格 -->
      <el-table :data="configList" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="courseName" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="签到时间窗口" min-width="220">
          <template #default="{ row }">
            <template v-if="row.timeWindows && row.timeWindows.length > 0">
              <el-tag
                v-for="(tw, i) in row.timeWindows"
                :key="i"
                size="small"
                style="margin: 2px 4px 2px 0"
              >
                {{ tw.startTime }} ~ {{ tw.endTime }}
              </el-tag>
            </template>
            <span v-else class="text-muted">未设置</span>
          </template>
        </el-table-column>
        <el-table-column prop="signCode" label="签到码" width="100" />
        <el-table-column prop="address" label="签到地址" min-width="140" show-overflow-tooltip />
        <el-table-column label="经纬度" width="170">
          <template #default="{ row }">
            <span v-if="row.latitude && row.longitude">
              {{ Number(row.latitude).toFixed(4) }}, {{ Number(row.longitude).toFixed(4) }}
            </span>
            <span v-else class="text-muted">未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑签到配置' : '添加签到配置'"
      width="600px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="选择课程" prop="courseName">
          <el-select
            v-model="form.courseName"
            filterable
            placeholder="请选择课程"
            :disabled="isEdit"
            style="width: 100%"
            @change="onCourseChange"
          >
            <el-option
              v-for="c in courseStore.courseList"
              :key="c.id"
              :label="c.courseName"
              :value="c.courseName"
            />
          </el-select>
        </el-form-item>

        <!-- 时间窗口列表 -->
        <el-form-item label="签到时间">
          <div class="time-windows-section">
            <div
              v-for="(tw, idx) in form.timeWindows"
              :key="idx"
              class="time-window-row"
            >
              <el-time-picker
                v-model="tw.startTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="开始"
                style="width: 120px"
              />
              <span class="time-sep">~</span>
              <el-time-picker
                v-model="tw.endTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="结束"
                style="width: 120px"
              />
              <el-button
                type="danger"
                :icon="Delete"
                circle
                size="small"
                @click="removeTimeWindow(idx)"
              />
            </div>
            <el-button type="primary" link :icon="Plus" @click="addTimeWindow">
              添加时间区间
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="签到码" prop="signCode">
          <el-input v-model="form.signCode" placeholder="手势签到码/普通签到码" />
        </el-form-item>
        <el-form-item label="签到地址" prop="address">
          <el-input v-model="form.address" placeholder="签到地址描述" />
        </el-form-item>
        <el-form-item label="纬度" prop="latitude">
          <el-input-number v-model="form.latitude" :precision="8" :step="0.0001" placeholder="纬度" style="width: 100%" />
        </el-form-item>
        <el-form-item label="经度" prop="longitude">
          <el-input-number v-model="form.longitude" :precision="8" :step="0.0001" placeholder="经度" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Setting, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { signConfigApi } from '@/api/signConfig'
import { courseApi } from '@/api/course'
import { useCourseStore } from '@/stores/course'
import type { SignConfig, SignConfigRequest, SignTimeWindow, ScheduleData } from '@/types'

const configList = ref<SignConfig[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const courseStore = useCourseStore()

interface FormState {
  courseName: string
  signCode: string
  address: string
  latitude: number | undefined
  longitude: number | undefined
  timeWindows: SignTimeWindow[]
}

const form = reactive<FormState>({
  courseName: '',
  signCode: '',
  address: '',
  latitude: undefined,
  longitude: undefined,
  timeWindows: [],
})

const rules: FormRules = {
  courseName: [{ required: true, message: '请选择课程', trigger: 'change' }],
}

// ============ 时间窗口操作 ============

function addTimeWindow() {
  form.timeWindows.push({ startTime: '', endTime: '' })
}

function removeTimeWindow(idx: number) {
  form.timeWindows.splice(idx, 1)
}

/** 根据课程名从课表中匹配时间，生成默认时间窗口（上课开始 ~ 上课开始+3分钟） */
async function onCourseChange(courseName: string) {
  if (!courseName || isEdit.value) return
  try {
    const res = await courseApi.getSchedule()
    const schedule: ScheduleData = res.data.data
    if (!schedule?.lessonArray || !schedule?.curriculum?.lessonTimeConfigArray) return

    // 找到该课程的所有排课（同一课程可能在不同星期有不同时间）
    const lessons = schedule.lessonArray.filter(l => l.name === courseName)
    if (lessons.length === 0) return

    const times = schedule.curriculum.lessonTimeConfigArray
    const newWindows: SignTimeWindow[] = []

    for (const lesson of lessons) {
      const startIdx = lesson.beginNumber - 1
      if (startIdx < 0 || startIdx >= times.length) continue

      const startTime = times[startIdx]
      // 结束时间 = 开始时间 + 3 分钟
      const [h, m] = startTime.split(':').map(Number)
      const endDate = new Date(2000, 0, 1, h, m + 3)
      const endTime = `${String(endDate.getHours()).padStart(2, '0')}:${String(endDate.getMinutes()).padStart(2, '0')}`

      newWindows.push({ startTime, endTime })
    }

    if (newWindows.length > 0) {
      form.timeWindows = newWindows
    }
  } catch {
    // 获取课表失败，不清空已填写的内容
  }
}

// ============ CRUD ============

async function fetchConfigs() {
  loading.value = true
  try {
    const res = await signConfigApi.getList()
    configList.value = res.data.data
  } finally {
    loading.value = false
  }
}

function openAddDialog() {
  isEdit.value = false
  editId.value = 0
  resetForm()
  dialogVisible.value = true
  if (courseStore.courseList.length === 0) {
    courseStore.fetchCourses()
  }
}

function openEditDialog(row: SignConfig) {
  isEdit.value = true
  editId.value = row.id
  form.courseName = row.courseName
  form.signCode = row.signCode
  form.address = row.address
  form.latitude = row.latitude
  form.longitude = row.longitude
  form.timeWindows = (row.timeWindows && row.timeWindows.length > 0)
    ? row.timeWindows.map(tw => ({ ...tw }))
    : (row.signStartTime && row.signEndTime
      ? [{ startTime: row.signStartTime, endTime: row.signEndTime }]
      : [])
  dialogVisible.value = true
}

function resetForm() {
  form.courseName = ''
  form.signCode = ''
  form.address = ''
  form.latitude = undefined
  form.longitude = undefined
  form.timeWindows = []
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    const data: SignConfigRequest = {
      courseName: form.courseName,
      signCode: form.signCode,
      address: form.address,
      latitude: form.latitude,
      longitude: form.longitude,
      timeWindows: form.timeWindows.filter(tw => tw.startTime && tw.endTime),
    }
    if (isEdit.value) {
      await signConfigApi.update(editId.value, data)
      ElMessage.success('更新成功')
    } else {
      await signConfigApi.add(data)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    fetchConfigs()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: SignConfig) {
  try {
    await ElMessageBox.confirm(`确定要删除课程"${row.courseName}"的签到配置吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    await signConfigApi.delete(row.id)
    ElMessage.success('删除成功')
    fetchConfigs()
  } catch { /* cancelled */ }
}

onMounted(() => {
  fetchConfigs()
  if (courseStore.courseList.length === 0) {
    courseStore.fetchCourses()
  }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.page-header span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 16px;
}

.text-muted {
  color: #c0c4cc;
}

/* 时间窗口区域 */
.time-windows-section {
  width: 100%;
}

.time-window-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.time-sep {
  color: #909399;
  flex-shrink: 0;
}

/* ===== 移动端 ===== */
@media (max-width: 767px) {
  .page-header span {
    font-size: 14px;
  }

  .time-window-row {
    flex-wrap: wrap;
  }

  .time-sep {
    display: none;
  }
}
</style>
