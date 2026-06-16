// ==========================================
// API 统一响应类型
// ==========================================
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// ==========================================
// 分页数据
// ==========================================
export interface PageData<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ==========================================
// 用户相关
// ==========================================
export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  realName: string
  uid: string
  cookieValid: boolean
}

export interface UserInfo {
  uid: string
  realName: string
  school: string
  phone: string
  email: string
  studentNo: string
}

// ==========================================
// 课程相关
// ==========================================
export interface Course {
  id: number
  userId: number
  courseId: string
  classId: string
  courseName: string
  teacherName: string
  schoolName: string
  folderName: string | null
  coverUrl: string
  startDate: string
  endDate: string
  dayOfWeek: number
  status: number
  progressDone: number
  progressTotal: number
}

// ==========================================
// 文件夹
// ==========================================
export interface CourseFolder {
  id: string
  name: string
}

// ==========================================
// 课程表相关
// ==========================================
export interface LessonItem {
  id: number
  name: string
  teacherName: string
  className: string
  location: string
  dayOfWeek: number
  beginNumber: number
  length: number
  weeks: number[]
}

export interface CurriculumInfo {
  firstWeekDateReal: number
  currentWeek: number
  maxWeek: number
  lessonTimeConfigArray: string[]
}

export interface ScheduleData {
  curriculum: CurriculumInfo
  lessonArray: LessonItem[]
}

// ==========================================
// 签到配置
// ==========================================
export interface SignTimeWindow {
  id?: number
  configId?: number
  startTime: string  // HH:mm
  endTime: string    // HH:mm
}

export interface SignConfig {
  id: number
  userId: number
  courseName: string
  signCode: string
  address: string
  latitude: number
  longitude: number
  signStartTime: string
  signEndTime: string
  timeWindows: SignTimeWindow[]
  createdAt: string
  updatedAt: string
}

export interface SignConfigRequest {
  courseName: string
  signCode?: string
  address?: string
  latitude?: number
  longitude?: number
  signStartTime?: string
  signEndTime?: string
  timeWindows?: SignTimeWindow[]
}

// ==========================================
// 签到记录
// ==========================================
export interface SignRecord {
  id: number
  userId: number
  courseId: string
  courseName: string
  activeId: string
  signType: string
  signTime: string
  status: string
  resultMsg: string
}

// ==========================================
// 签到日志
// ==========================================
export interface SignLog {
  id: number
  userId: number
  logType: string
  message: string
  createdAt: string
}

// ==========================================
// 任务状态
// ==========================================
export interface TaskStatus {
  status: string
  lastRunAt: string
  nextRunAt: string
}
