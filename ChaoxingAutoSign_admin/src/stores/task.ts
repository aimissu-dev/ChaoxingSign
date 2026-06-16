import { defineStore } from 'pinia'
import { ref } from 'vue'
import { taskApi } from '@/api/task'
import type { TaskStatus } from '@/types'

export const useTaskStore = defineStore('task', () => {
  const taskStatus = ref<TaskStatus>({ status: 'stopped', lastRunAt: '', nextRunAt: '' })
  const loading = ref(false)
  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function startTask() {
    loading.value = true
    try {
      await taskApi.start()
      taskStatus.value.status = 'running'
      startPolling()
    } finally {
      loading.value = false
    }
  }

  async function stopTask() {
    loading.value = true
    try {
      await taskApi.stop()
      taskStatus.value.status = 'stopped'
      stopPolling()
    } finally {
      loading.value = false
    }
  }

  async function fetchStatus() {
    try {
      const res = await taskApi.getStatus()
      taskStatus.value = res.data.data
    } catch {
      // ignore
    }
  }

  function startPolling() {
    stopPolling()
    pollTimer = setInterval(fetchStatus, 5000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return { taskStatus, loading, startTask, stopTask, fetchStatus, startPolling, stopPolling }
})
