import { ref, shallowRef } from 'vue'
import { errorMessage } from '../lib/errors'

/**
 * Bọc một thao tác bất đồng bộ và theo dõi loading + error — thay cho mẫu lặp
 * `ref(loading)/ref(error)/try-catch` ở mọi view.
 *
 * Dùng cho cả tải dữ liệu lẫn mutation:
 *   const { loading, error, run } = useAsync(() => store.fetchAll(), 'Không tải được.')
 *   onMounted(run)
 *
 * `run` ném lại lỗi để caller có thể rẽ nhánh (vd: đóng modal khi thành công);
 * `error` đã được set sẵn để hiển thị.
 */
export function useAsync<T, A extends unknown[]>(
  fn: (...args: A) => Promise<T>,
  fallback?: string,
) {
  const data = shallowRef<T | null>(null)
  const error = ref<string | null>(null)
  const loading = ref(false)

  async function run(...args: A): Promise<T> {
    loading.value = true
    error.value = null
    try {
      const result = await fn(...args)
      data.value = result
      return result
    } catch (e) {
      error.value = errorMessage(e, fallback)
      throw e
    } finally {
      loading.value = false
    }
  }

  return { data, error, loading, run }
}
