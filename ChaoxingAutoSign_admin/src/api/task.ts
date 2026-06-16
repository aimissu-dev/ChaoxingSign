import request from './request'
import type { ApiResponse, TaskStatus, SignRecord, SignLog, PageData } from '@/types'

export const taskApi = {
  start() {
    return request.post<ApiResponse<null>>('/api/task/start')
  },

  stop() {
    return request.post<ApiResponse<null>>('/api/task/stop')
  },

  getStatus() {
    return request.get<ApiResponse<TaskStatus>>('/api/task/status')
  },
}

export const signRecordApi = {
  getList(page = 1, size = 10) {
    return request.get<ApiResponse<PageData<SignRecord>>>(`/api/sign-record/list?page=${page}&size=${size}`)
  },

  getLogs(page = 1, size = 20) {
    return request.get<ApiResponse<PageData<SignLog>>>(`/api/sign-record/logs?page=${page}&size=${size}`)
  },

  clearLogs() {
    return request.delete<ApiResponse<null>>('/api/sign-record/logs')
  },

  deleteLog(id: number) {
    return request.delete<ApiResponse<null>>(`/api/sign-record/logs/${id}`)
  },
}
