import { useEffect, useState, type DependencyList } from 'react'
import { errorMessage } from '../lib/errors'

interface AsyncState<T> {
  data: T | null
  error: string | null
  loading: boolean
}

/**
 * Tải dữ liệu khi mount (và khi deps đổi) với loading + error — thay mẫu lặp
 * `useState(null) + useState(error) + useEffect(...).then().catch()` ở các page.
 * Tự huỷ khi unmount để tránh set state trên component đã gỡ.
 */
export function useAsync<T>(fn: () => Promise<T>, deps: DependencyList, fallback?: string) {
  const [state, setState] = useState<AsyncState<T>>({ data: null, error: null, loading: true })

  useEffect(() => {
    let cancelled = false
    // Reset cho lần tải mới khi deps đổi — đây là hook đồng bộ dữ liệu từ API, set state ngay
    // là chủ đích (không phải cascading render thừa).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setState({ data: null, error: null, loading: true })
    fn()
      .then((d) => {
        if (!cancelled) setState({ data: d, error: null, loading: false })
      })
      .catch((e) => {
        if (!cancelled) setState({ data: null, error: errorMessage(e, fallback), loading: false })
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return state
}
