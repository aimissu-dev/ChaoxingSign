import request from './request'
import type { ApiResponse, LoginRequest, LoginResponse } from '@/types'

export const authApi = {
  login(data: LoginRequest) {
    return request.post<ApiResponse<LoginResponse>>('/api/auth/login', data)
  },

  logout() {
    return request.post<ApiResponse<null>>('/api/auth/logout')
  },
}
