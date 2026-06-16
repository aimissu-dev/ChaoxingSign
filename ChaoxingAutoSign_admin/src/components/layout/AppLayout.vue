<template>
  <div class="layout">
    <!-- 移动端遮罩层 -->
    <div
      v-if="isMobile && sidebarOpen"
      class="sidebar-overlay"
      @click="closeSidebar"
    />

    <!-- 侧边栏 -->
    <el-aside
      :class="['sidebar', {
        'sidebar-mobile': isMobile,
        'sidebar-mobile-open': isMobile && sidebarOpen,
      }]"
      :style="isMobile ? {} : { width: isCollapse ? '64px' : '220px' }"
    >
      <div class="logo">
        <span v-show="!isCollapse || isMobile" class="logo-text">超星自动签到</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse && !isMobile"
        :router="true"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        @select="onMenuSelect"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/schedule">
          <el-icon><Calendar /></el-icon>
          <span>课程表</span>
        </el-menu-item>
        <el-menu-item index="/courses">
          <el-icon><Reading /></el-icon>
          <span>课程管理</span>
        </el-menu-item>
        <el-menu-item index="/sign-config">
          <el-icon><Setting /></el-icon>
          <span>签到配置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主区域 -->
    <div class="main-area">
      <el-header class="header">
        <div class="header-left">
          <el-button text @click="toggleSidebar">
            <el-icon :size="20">
              <component :is="isMobile
                ? (sidebarOpen ? Close : MenuIcon)
                : (isCollapse ? Expand : Fold)
              " />
            </el-icon>
          </el-button>
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <span class="username">{{ authStore.username || '未登录' }}</span>
          <el-button type="danger" text @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="content">
        <router-view />
      </el-main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Odometer, Reading, Calendar, Setting, Fold, Expand, Menu as MenuIcon, Close } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useTaskStore } from '@/stores/task'
import { authApi } from '@/api/auth'
import { useResponsive } from '@/composables/useResponsive'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const taskStore = useTaskStore()
const { isMobile } = useResponsive()

const isCollapse = ref(false)
const sidebarOpen = ref(false)

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title as string || '')

function toggleSidebar() {
  if (isMobile.value) {
    sidebarOpen.value = !sidebarOpen.value
  } else {
    isCollapse.value = !isCollapse.value
  }
}

function closeSidebar() {
  sidebarOpen.value = false
}

function onMenuSelect() {
  if (isMobile.value) {
    closeSidebar()
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    taskStore.stopPolling()
    try { await authApi.logout() } catch { /* ignore */ }
    authStore.logout()
    router.push('/login')
  } catch { /* cancelled */ }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ===== 侧边栏基底 ===== */
.sidebar {
  background-color: #304156;
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s;
}

/* ===== 移动端侧边栏 ===== */
.sidebar-mobile {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 1001;
  width: 240px !important;
  transform: translateX(-100%);
  transition: transform 0.3s ease;
}

.sidebar-mobile-open {
  transform: translateX(0);
}

.sidebar-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.45);
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-img {
  width: 32px;
  height: 32px;
}

.logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* ===== 顶栏 ===== */
.header {
  height: 60px !important;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 16px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.breadcrumb {
  overflow: hidden;
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.username {
  color: #606266;
  font-size: 14px;
}

.content {
  flex: 1;
  overflow-y: auto;
  background: #f0f2f5;
  padding: 16px;
}

/* ===== 平板：侧边栏默认折叠 ===== */
@media (min-width: 768px) and (max-width: 1023px) {
  .sidebar {
    width: 64px !important;
  }
  .sidebar .logo-text {
    display: none;
  }
}

/* ===== 手机端优化 ===== */
@media (max-width: 767px) {
  .header {
    padding: 0 12px;
  }

  .content {
    padding: 12px;
  }

  .breadcrumb {
    display: none;
  }

  .username {
    display: none;
  }
}
</style>
