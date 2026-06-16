import { defineStore } from 'pinia'
import { ref } from 'vue'
import { courseApi } from '@/api/course'
import type { Course, CourseFolder } from '@/types'

export const useCourseStore = defineStore('course', () => {
  const courseList = ref<Course[]>([])
  const folderList = ref<CourseFolder[]>([])
  const loading = ref(false)

  async function fetchCourses() {
    loading.value = true
    try {
      const res = await courseApi.getCourseList()
      courseList.value = res.data.data
    } finally {
      loading.value = false
    }
  }

  async function fetchFolders() {
    try {
      const res = await courseApi.getFolders()
      folderList.value = res.data.data
    } catch {
      folderList.value = []
    }
  }

  return { courseList, folderList, loading, fetchCourses, fetchFolders }
})
