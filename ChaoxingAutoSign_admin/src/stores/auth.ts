import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { LoginRequest, UserInfo } from '@/types'
import { userApi } from '@/api/user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const username = ref(localStorage.getItem('username') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = ref(!!token.value)

  async function login(data: LoginRequest) {
    const res = await authApi.login(data)
    const result = res.data.data
    token.value = result.token
    userId.value = result.userId
    username.value = result.username
    isLoggedIn.value = true

    localStorage.setItem('token', result.token)
    localStorage.setItem('userId', String(result.userId))
    localStorage.setItem('username', result.username)

    await fetchUserInfo()
    return result
  }

  async function fetchUserInfo() {
    try {
      const res = await userApi.getUserInfo()
      userInfo.value = res.data.data
    } catch {
      // ignore
    }
  }

  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    userInfo.value = null
    isLoggedIn.value = false
    localStorage.clear()
  }

  return { token, userId, username, userInfo, isLoggedIn, login, fetchUserInfo, logout }
})
