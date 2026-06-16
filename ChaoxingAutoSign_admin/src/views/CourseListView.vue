<template>
  <div class="course-list-page">
    <el-card>
      <template #header>
        <div class="page-header">
          <span><el-icon><Reading /></el-icon> 课程管理</span>
          <el-button type="primary" @click="refreshAll" :loading="courseStore.loading">
            刷新课程
          </el-button>
        </div>
      </template>

      <!-- 文件夹导航 -->
      <div class="folder-nav" v-if="courseStore.courseList.length > 0">
        <div
          class="folder-tab"
          :class="{ active: selectedFolder === null }"
          @click="selectedFolder = null"
        >
          <el-icon><Grid /></el-icon>
          <span>全部</span>
          <el-tag size="small" type="info" class="count-tag">{{ filteredCourses.length }}</el-tag>
        </div>
        <div
          class="folder-tab"
          :class="{ active: selectedFolder === '__unclassified__' }"
          @click="selectedFolder = '__unclassified__'"
        >
          <el-icon><Document /></el-icon>
          <span>未分类</span>
          <el-tag size="small" type="info" class="count-tag">
            {{ getUnclassifiedCount() }}
          </el-tag>
        </div>
        <div
          v-for="folder in courseStore.folderList"
          :key="folder.id"
          class="folder-tab"
          :class="{ active: selectedFolder === folder.name }"
          @click="selectedFolder = folder.name"
        >
          <el-icon><FolderOpened /></el-icon>
          <span>{{ folder.name }}</span>
          <el-tag size="small" type="info" class="count-tag">
            {{ getFolderCourseCount(folder.name) }}
          </el-tag>
        </div>
      </div>

      <!-- 筛选条 -->
      <el-radio-group v-model="filter" style="margin: 16px 0">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="active">进行中</el-radio-button>
        <el-radio-button value="ended">已结束</el-radio-button>
      </el-radio-group>

      <!-- 当前文件夹路径面包屑 -->
      <div v-if="selectedFolder" class="folder-breadcrumb">
        <el-icon><FolderOpened /></el-icon>
        <span>{{ selectedFolder === '__unclassified__' ? '未分类' : selectedFolder }}</span>
        <el-tag size="small" type="warning">{{ folderDisplayedCourses.length }} 门课程</el-tag>
      </div>

      <el-empty v-if="folderDisplayedCourses.length === 0 && !courseStore.loading" description="暂无课程" />

      <!-- 课程卡片网格 -->
      <div v-loading="courseStore.loading">
        <div class="course-grid">
          <el-card
            v-for="course in folderDisplayedCourses"
            :key="course.id || course.courseId"
            shadow="hover"
            class="course-card"
            @click="openDetail(course)"
          >
            <div class="course-cover">
              <img
                :src="coverSrc(course.coverUrl)"
                alt="cover"
                @error="onImgError"
              />
            </div>
            <div class="course-info">
              <h4>{{ course.courseName }}</h4>
              <p><el-icon><User /></el-icon> {{ course.teacherName || '未知教师' }}</p>
              <p><el-icon><School /></el-icon> {{ course.schoolName || '--' }}</p>
              <div class="course-progress" v-if="course.progressTotal > 0">
                <el-progress
                  :percentage="Math.round((course.progressDone / course.progressTotal) * 100)"
                  :stroke-width="8"
                />
                <span class="progress-text">{{ course.progressDone }}/{{ course.progressTotal }} 任务点</span>
              </div>
              <div class="course-footer">
                <el-tag :type="course.status === 1 ? 'success' : 'info'" size="small">
                  {{ course.status === 1 ? '进行中' : '已结束' }}
                </el-tag>
                <span class="course-date" v-if="course.startDate">{{ course.startDate }} ~ {{ course.endDate }}</span>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </el-card>

    <!-- 课程详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="detailCourse?.courseName"
      width="560px"
      destroy-on-close
    >
      <div v-if="detailCourse" class="course-detail">
        <div class="detail-cover">
          <img
            :src="coverSrc(detailCourse?.coverUrl || '')"
            alt="cover"
            @error="onImgError"
          />
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="课程名称" :span="2">
            {{ detailCourse.courseName }}
          </el-descriptions-item>
          <el-descriptions-item label="教师">
            {{ detailCourse.teacherName || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="学校">
            {{ detailCourse.schoolName || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="课程ID">
            {{ detailCourse.courseId }}
          </el-descriptions-item>
          <el-descriptions-item label="班级ID">
            {{ detailCourse.classId || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="文件夹" v-if="detailCourse.folderName">
            {{ detailCourse.folderName }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailCourse.status === 1 ? 'success' : 'info'" size="small">
              {{ detailCourse.status === 1 ? '进行中' : '已结束' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开课日期" :span="2" v-if="detailCourse.startDate">
            {{ detailCourse.startDate }} ~ {{ detailCourse.endDate }}
          </el-descriptions-item>
          <el-descriptions-item label="学习进度" :span="2" v-if="detailCourse.progressTotal > 0">
            <div class="detail-progress">
              <el-progress
                :percentage="Math.round((detailCourse.progressDone / detailCourse.progressTotal) * 100)"
                :stroke-width="12"
              />
              <span>{{ detailCourse.progressDone }} / {{ detailCourse.progressTotal }} 任务点</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="上课星期" v-if="detailCourse.dayOfWeek">
            周{{ ['一','二','三','四','五','六','日'][detailCourse.dayOfWeek - 1] || detailCourse.dayOfWeek }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Reading, User, School, FolderOpened, Grid, Document } from '@element-plus/icons-vue'
import { useCourseStore } from '@/stores/course'
import { proxyCoverUrl } from '@/api/course'
import type { Course } from '@/types'

const courseStore = useCourseStore()
const filter = ref('all')
const selectedFolder = ref<string | null>(null)

// 课程详情弹窗
const detailVisible = ref(false)
const detailCourse = ref<Course | null>(null)

const placeholderImage = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="280" height="140">'
  + '<rect fill="#e4e7ed" width="280" height="140"/>'
  + '<text x="50%" y="50%" fill="#909399" text-anchor="middle" dy=".3em" font-size="14" font-family="sans-serif">暂无封面</text>'
  + '</svg>'
)

function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  img.src = placeholderImage
}

function coverSrc(rawUrl: string): string {
  return rawUrl ? proxyCoverUrl(rawUrl) : placeholderImage
}

function openDetail(course: Course) {
  detailCourse.value = course
  detailVisible.value = true
}

/** 计算指定文件夹下的课程数量（考虑当前 filter） */
function getFolderCourseCount(folderName: string): number {
  return filteredCourses.value.filter(c => c.folderName === folderName).length
}

/** 计算未分类课程数量 */
function getUnclassifiedCount(): number {
  return filteredCourses.value.filter(c => !c.folderName).length
}

/** 全部筛选后的课程 */
const filteredCourses = computed(() => {
  if (filter.value === 'all') return courseStore.courseList
  if (filter.value === 'active') return courseStore.courseList.filter(c => c.status === 1)
  return courseStore.courseList.filter(c => c.status === 0)
})

/** 按文件夹筛选 + 状态筛选后的课程 */
const folderDisplayedCourses = computed(() => {
  if (selectedFolder.value === null) return filteredCourses.value
  if (selectedFolder.value === '__unclassified__') return filteredCourses.value.filter(c => !c.folderName)
  return filteredCourses.value.filter(c => c.folderName === selectedFolder.value)
})

async function refreshAll() {
  await courseStore.fetchCourses()
  await courseStore.fetchFolders()
}

onMounted(async () => {
  await refreshAll()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.page-header span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 16px;
}

/* 文件夹导航 */
.folder-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.folder-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: all 0.2s;
  user-select: none;
}
.folder-tab:hover {
  background: #ecf5ff;
  color: #409eff;
}
.folder-tab.active {
  background: #409eff;
  color: #fff;
}
.folder-tab.active .count-tag {
  background: rgba(255, 255, 255, 0.25);
  border-color: transparent;
  color: #fff;
}
.folder-tab .count-tag {
  font-size: 12px;
  padding: 0 6px;
  height: 20px;
  line-height: 20px;
}

/* 文件夹面包屑 */
.folder-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.course-card {
  transition: transform 0.2s;
  cursor: pointer;
}
.course-card:hover {
  transform: translateY(-4px);
}

.course-cover img {
  width: 100%;
  height: 140px;
  object-fit: cover;
  border-radius: 4px;
  background: #e4e7ed;
}

.course-info h4 {
  margin: 12px 0 8px;
  font-size: 15px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-info p {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 4px 0;
  font-size: 13px;
  color: #909399;
}

.course-progress {
  margin: 12px 0 8px;
}
.progress-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  display: block;
}

.course-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.course-date {
  font-size: 12px;
  color: #c0c4cc;
}

/* 课程详情弹窗 */
.course-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-cover img {
  width: 100%;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
  background: #e4e7ed;
}
.detail-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.detail-progress .el-progress {
  flex: 1;
}
.detail-progress span {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

/* ===== 移动端 ===== */
@media (max-width: 767px) {
  .course-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 12px;
  }

  .course-cover img {
    height: 120px;
  }

  .course-info h4 {
    font-size: 14px;
  }

  .folder-tab {
    padding: 6px 10px;
    font-size: 12px;
  }

  .page-header span {
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>
