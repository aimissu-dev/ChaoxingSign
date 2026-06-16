import { ref, onMounted, onUnmounted } from 'vue'

/** 断点常量 */
export const BREAKPOINTS = {
  sm: 576,
  md: 768,
  lg: 1024,
  xl: 1280,
} as const

export function useResponsive() {
  const width = ref(window.innerWidth)
  const height = ref(window.innerHeight)

  const isMobile = ref(false)
  const isTablet = ref(false)
  const isDesktop = ref(false)

  function update() {
    width.value = window.innerWidth
    height.value = window.innerHeight
    isMobile.value = width.value < BREAKPOINTS.md
    isTablet.value = width.value >= BREAKPOINTS.md && width.value < BREAKPOINTS.lg
    isDesktop.value = width.value >= BREAKPOINTS.lg
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', update)
  })

  return { width, height, isMobile, isTablet, isDesktop }
}
