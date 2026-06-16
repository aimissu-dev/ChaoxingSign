import request from './request'
import type { ApiResponse, SignConfig, SignConfigRequest } from '@/types'

export const signConfigApi = {
  getList() {
    return request.get<ApiResponse<SignConfig[]>>('/api/sign-config/list')
  },

  add(data: SignConfigRequest) {
    return request.post<ApiResponse<SignConfig>>('/api/sign-config', data)
  },

  update(id: number, data: SignConfigRequest) {
    return request.put<ApiResponse<SignConfig>>(`/api/sign-config/${id}`, data)
  },

  delete(id: number) {
    return request.delete<ApiResponse<null>>(`/api/sign-config/${id}`)
  },
}
