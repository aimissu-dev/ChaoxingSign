import request from './request'
import type { ApiResponse, UserInfo } from '@/types'

export const userApi = {
  getUserInfo() {
    return request.get<ApiResponse<UserInfo>>('/api/user/info')
  },
}
