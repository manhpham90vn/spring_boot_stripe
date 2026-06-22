<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useWaitingRoomStore } from '../stores/waitingRoom'
import { useEventsStore } from '../stores/events'
import { useAsync } from '../composables/useAsync'
import { errorMessage } from '../lib/errors'
import { formatDuration } from '../lib/format'
import PageHeader from '../components/PageHeader.vue'
import type { AdmissionConfig } from '../types/waitingRoom'

const store = useWaitingRoomStore()
const eventsStore = useEventsStore()

// Cấu hình van Waiting Room. Lưu ở Redis của waitingroom (store chính — kiến trúc không dùng
// PostgreSQL cho service này); cập nhật áp dụng NGAY cho bộ thả admission, không cần redeploy.
const form = reactive<AdmissionConfig>({ rate: 20, tokenTtlSeconds: 1800, admitTtlSeconds: 120 })
const saving = ref(false)
const saved = ref(false)

const { error, loading, run: loadConfig } = useAsync(async () => {
  const c = await store.fetchConfig()
  Object.assign(form, c)
}, 'Không tải được cấu hình.')

// --- Thống kê hàng chờ (poll định kỳ) ---
const statsError = ref<string | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

async function refreshStats() {
  try {
    await store.fetchStats()
    statsError.value = null
  } catch (e) {
    statsError.value = errorMessage(e, 'Không tải được thống kê.')
  }
}

// eventId → tiêu đề (dùng state events đã có; không fetch lại nếu đã nạp).
function eventLabel(id: string): string {
  return eventsStore.list.find((e) => e.id === id)?.title ?? id
}

onMounted(() => {
  loadConfig()
  eventsStore.fetchList().catch(() => {}) // có tên thì đẹp, không có cũng không chặn thống kê
  refreshStats()
  timer = setInterval(refreshStats, 5000) // poll 5s — đủ tươi cho quan sát flash sale
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function save() {
  saving.value = true
  error.value = null
  saved.value = false
  try {
    const c = await store.updateConfig({ ...form })
    Object.assign(form, c)
    saved.value = true
  } catch (e) {
    error.value = errorMessage(e, 'Lưu thất bại.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PageHeader
    title="Waiting Room"
    subtitle="Van chặn spike khi flash sale. Cấu hình áp dụng ngay cho bộ thả (admission), không cần khởi động lại service."
  />

  <!-- Thống kê hàng chờ theo thời gian thực -->
  <div class="panel" style="margin-bottom: 24px">
    <div class="row between" style="align-items: center; margin-bottom: 12px">
      <h2 style="margin: 0">Hàng chờ hiện tại</h2>
      <span class="muted" style="font-size: 13px">
        <template v-if="store.lastRefreshed">
          Cập nhật {{ store.lastRefreshed.toLocaleTimeString('vi-VN') }} · tự làm mới mỗi 5s
        </template>
      </span>
    </div>

    <div v-if="statsError" class="alert">{{ statsError }}</div>

    <p v-if="!statsError && store.stats.length === 0" class="muted">
      Hiện không có sự kiện nào đang có hàng chờ.
    </p>

    <template v-else-if="store.stats.length > 0">
      <table class="table">
        <thead>
          <tr>
            <th>Sự kiện</th>
            <th style="text-align: right">Đang chờ</th>
            <th style="text-align: right">Tổng đã thả</th>
            <th style="text-align: right">Nhịp thả</th>
            <th style="text-align: right" title="Thời gian dự kiến để thả hết hàng — cũng là thời gian người cuối hàng phải chờ (ước lượng theo nhịp thả hiện tại)">
              Chờ tối đa
            </th>
            <th>Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in store.stats" :key="s.eventId">
            <td>{{ eventLabel(s.eventId) }}</td>
            <td style="text-align: right">{{ s.waiting.toLocaleString('vi-VN') }}</td>
            <td style="text-align: right">{{ s.admittedTotal.toLocaleString('vi-VN') }}</td>
            <td style="text-align: right">{{ s.ratePerSecond }}/s</td>
            <td style="text-align: right">{{ formatDuration(s.etaSeconds) }}</td>
            <td>
              <span v-if="s.soldOut" class="badge badge--CANCELLED">Hết vé</span>
              <span v-else class="badge badge--ON_SALE">Đang thả</span>
            </td>
          </tr>
        </tbody>
      </table>

      <p class="muted" style="font-size: 13px; margin: 10px 0 0">
        <strong>Đang chờ</strong>: số người còn trong hàng · <strong>Tổng đã thả</strong>: số người
        đã được cho vào mua (cộng dồn) · <strong>Chờ tối đa</strong>: ước lượng thời gian người cuối
        hàng phải đợi theo nhịp thả hiện tại.
      </p>
    </template>
  </div>

  <h2 style="margin: 0 0 12px">Cấu hình admission</h2>
  <div v-if="error" class="alert">{{ error }}</div>
  <div v-if="loading" class="spinner">Đang tải…</div>

  <form v-else class="panel" style="max-width: 520px" @submit.prevent="save">
    <div class="field">
      <label>Nhịp thả mặc định (người / giây)</label>
      <input v-model.number="form.rate" type="number" min="1" required />
      <p class="muted" style="font-size: 13px; margin: 6px 0 0">
        Đặt theo throughput thật của Order/Inventory/Stripe (~100–200 req/s Stripe là trần cứng).
        Mỗi sự kiện vẫn có thể override riêng.
      </p>
    </div>

    <div class="field">
      <label>Hạn chỗ trong hàng — token TTL (giây)</label>
      <input v-model.number="form.tokenTtlSeconds" type="number" min="1" required />
      <p class="muted" style="font-size: 13px; margin: 6px 0 0">
        Một chỗ trong hàng hết hạn sau bao lâu nếu khách rời đi (chống rác hàng đợi).
      </p>
    </div>

    <div class="field">
      <label>Hạn PASS — admit TTL (giây)</label>
      <input v-model.number="form.admitTtlSeconds" type="number" min="1" required />
      <p class="muted" style="font-size: 13px; margin: 6px 0 0">
        Sau khi được thả, khách phải vào mua trong khoảng này; quá hạn phải xếp lại
        (chống giữ chỗ vô hạn).
      </p>
    </div>

    <div class="row between" style="margin-top: 18px; align-items: center">
      <span v-if="saved" class="muted">✓ Đã lưu</span>
      <span v-else></span>
      <button class="btn btn--primary" :disabled="saving">
        {{ saving ? 'Đang lưu…' : 'Lưu cấu hình' }}
      </button>
    </div>
  </form>
</template>
