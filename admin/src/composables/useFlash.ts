import { ref } from 'vue'

/** Thông báo thoáng qua (toast đơn giản) — tự ẩn sau `ms`. */
export function useFlash(ms = 2500) {
  const message = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null

  function flash(msg: string) {
    message.value = msg
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => (message.value = null), ms)
  }

  return { message, flash }
}
