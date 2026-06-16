<template>
  <div class="dashboard">
    <!-- 用户信息卡片 -->
    <el-row :gutter="20" class="top-row">
      <el-col :xs="24" :sm="24" :md="8" class="col-item">
        <el-card shadow="hover" class="top-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><User /></el-icon> 用户信息</span>
            </div>
          </template>
          <div class="user-info" v-if="authStore.userInfo">
            <p><strong>姓名：</strong>{{ authStore.userInfo.realName || '--' }}</p>
            <p><strong>学校：</strong>{{ authStore.userInfo.school || '--' }}</p>
            <p><strong>学号：</strong>{{ authStore.userInfo.studentNo || '--' }}</p>
            <p><strong>手机：</strong>{{ authStore.userInfo.phone || '--' }}</p>
            <p><strong>邮箱：</strong>{{ authStore.userInfo.email || '--' }}</p>
          </div>
          <div v-else class="user-info">
            <p>加载中...</p>
          </div>
        </el-card>
      </el-col>

      <!-- 任务控制卡片 -->
      <el-col :xs="24" :sm="12" :md="8" class="col-item">
        <el-card shadow="hover" class="top-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><VideoPlay /></el-icon> 签到任务</span>
            </div>
          </template>
          <div class="task-control">
            <p class="task-status-text">
              状态：
              <el-tag :type="taskStore.taskStatus.status === 'running' ? 'success' : 'info'">
                {{ taskStore.taskStatus.status === 'running' ? '运行中' : '已停止' }}
              </el-tag>
            </p>
            <p v-if="taskStore.taskStatus.lastRunAt">
              上次执行：{{ taskStore.taskStatus.lastRunAt }}
            </p>
            <div class="task-actions">
              <el-button
                type="success"
                :disabled="taskStore.taskStatus.status === 'running'"
                :loading="taskStore.loading"
                @click="taskStore.startTask()"
                class="ml-10"
              >
                启动任务
              </el-button>
              <el-button
                type="danger"
                :disabled="taskStore.taskStatus.status !== 'running'"
                :loading="taskStore.loading"
                @click="taskStore.stopTask()"
                class="ml-10"
              >
                停止任务
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 统计卡片 -->
      <el-col :xs="24" :sm="12" :md="8" class="col-item">
        <el-card shadow="hover" class="top-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><DataAnalysis /></el-icon> 签到统计</span>
            </div>
          </template>
          <div class="stats">
            <div class="stat-item">
              <span class="stat-value">{{ totalToday }}</span>
              <span class="stat-label">今日签到</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ totalRecords }}</span>
              <span class="stat-label">累计签到</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 签到日志 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Document /></el-icon> 签到日志</span>
              <el-button type="danger" size="small" text @click="clearLogs">清空日志</el-button>
            </div>
          </template>
          <el-table :data="logs" stripe height="400" v-loading="logsLoading">
            <el-table-column prop="id" label="序号" width="60" />
            <el-table-column prop="logType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="logTypeColor(row.logType)"
                  size="small"
                >{{ row.logType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="消息" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="280" />
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" size="small" text @click="deleteLogItem(row.id)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { User, VideoPlay, DataAnalysis, Document, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useTaskStore } from '@/stores/task'
import { signRecordApi } from '@/api/task'
import type { SignRecord, SignLog } from '@/types'

const authStore = useAuthStore()
const taskStore = useTaskStore()

const records = ref<SignRecord[]>([])
const totalToday = ref(0)
const totalRecords = ref(0)
const logs = ref<SignLog[]>([])
const logsLoading = ref(false)
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

function logTypeColor(type: string): 'success' | 'warning' | 'danger' | 'info' | '' {
  const map: Record<string, any> = {
    success: 'success',
    info: 'info',
    warn: 'warning',
    error: 'danger',
  }
  return map[type] || 'info'
}

async function fetchRecords() {
  try {
    const res = await signRecordApi.getList(1, 50)
    const data = res.data.data
    const all = data.content || []
    records.value = all
    totalRecords.value = data.totalElements ?? all.length
    // 今日签到：统计 signTime 为今天的记录数
    const today = new Date().toISOString().slice(0, 10)
    totalToday.value = all.filter(r => r.signTime?.startsWith(today)).length
  } catch { /* ignore */ }
}

async function fetchLogs() {
  logsLoading.value = true
  try {
    const res = await signRecordApi.getLogs(1, 50)
    logs.value = res.data.data.content
  } finally {
    logsLoading.value = false
  }
}

async function deleteLogItem(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除这条日志吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    await signRecordApi.deleteLog(id)
    logs.value = logs.value.filter(l => l.id !== id)
    ElMessage.success('日志已删除')
  } catch { /* ignore */ }
}

async function clearLogs() {
  try {
    await ElMessageBox.confirm('确定要清空所有日志吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    await signRecordApi.clearLogs()
    logs.value = []
    ElMessage.success('日志已清空')
  } catch { /* cancelled */ }
}

function pollData() {
  fetchRecords()
  fetchLogs()
}

onMounted(async () => {
  await authStore.fetchUserInfo()
  await taskStore.fetchStatus()
  if (taskStore.taskStatus.status === 'running') {
    taskStore.startPolling()
  }
  pollData()
  pollTimer.value = setInterval(pollData, 5000)
})

onUnmounted(() => {
  taskStore.stopPolling()
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
}

/* 顶部三列等高 */
.top-row .el-col {
  display: flex;
}

.top-row .col-item {
  margin-bottom: 16px;
}

.top-card {
  width: 100%;
}

.top-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 180px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.user-info p {
  margin: 8px 0;
  font-size: 14px;
  color: #606266;
}

.task-control {
  text-align: center;
}

.task-status-text {
  font-size: 16px;
  margin-bottom: 12px;
}

.task-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 16px;
}

.stats {
  display: flex;
  justify-content: space-around;
}

.stat-item {
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

/* ===== 移动端 ===== */
@media (max-width: 767px) {
  .top-card :deep(.el-card__body) {
    min-height: auto;
    padding: 16px;
  }

  .ml-10{
    margin: 0;
  }

  .top-card :deep(.el-card__header) {
    padding: 10px 16px;
  }

  .task-actions {
    flex-direction: column;
    gap: 8px;
  }

  .task-actions .el-button {
    width: 100%;
  }

  .stat-value {
    font-size: 26px;
  }

  .stat-label {
    font-size: 13px;
  }
}
</style>
