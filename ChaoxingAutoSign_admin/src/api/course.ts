import request from './request'
import type { ApiResponse, Course, CourseFolder, ScheduleData } from '@/types'

/** 将超星封面 URL 转为后端代理 URL（解决跨域） */
export function proxyCoverUrl(originalUrl: string): string {
  if (!originalUrl || originalUrl.startsWith('data:') || originalUrl.startsWith('blob:')) {
    return originalUrl
  }
  const utf8 = new TextEncoder().encode(originalUrl)
  const binary = String.fromCharCode(...utf8)
  const base64 = btoa(binary)
  const encoded = base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `/api/course/cover?url=${encoded}`
}

export const courseApi = {
  getCourseList() {
    return request.get<ApiResponse<Course[]>>('/api/course/list')
  },

  getFolders() {
    return request.get<ApiResponse<CourseFolder[]>>('/api/course/folders')
  },

  getSchedule(week?: number) {
    return request.get<ApiResponse<ScheduleData>>('/api/course/schedule', {
      params: { week },
    })
  },
}
