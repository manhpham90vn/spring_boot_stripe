<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { waitingroom, events } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { AdmissionConfig, EventStats } from '../api/types'

// Cấu hình van Waiting Room. Lưu ở Redis của waitingroom (store chính — kiến trúc không dùng
// PostgreSQL cho service này); cập nhật áp dụng NGAY cho bộ thả admission, không cần redeploy.
const form = reactive<AdmissionConfig>({ rate: 20, tokenTtlSeconds: 1800, admitTtlSeconds: 120 })
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const saved = ref(false)

// --- Thống kê hàng chờ (ảnh chụp tức thời, poll định kỳ) ---
const stats = ref<EventStats[]>([])
const statsError = ref<string | null>(null)
const lastRefreshed = ref<Date | null>(null)
// eventId → tiêu đề sự kiện (để hiển thị tên thay vì UUID).
const titles = reactive<Record<string, string>>({})
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  error.value = null
  try {
    const c = await waitingroom.getConfig()
    form.rate = c.rate
    form.tokenTtlSeconds = c.tokenTtlSeconds
    form.admitTtlSeconds = c.admitTtlSeconds
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Không tải được cấu hình.'
  } finally {
    loading.value = false
  }
}

async function loadTitles() {
  try {
    const list = await events.list()
    for (const ev of list) titles[ev.id] = ev.title
  } catch {
    // Không có tên sự kiện thì hiển thị UUID — không chặn thống kê.
  }
}

async function refreshStats() {
  try {
    stats.value = await waitingroom.stats()
    statsError.value = null
    lastRefreshed.value = new Date()
  } catch (e) {
    statsError.value = e instanceof ApiError ? e.message : 'Không tải được thống kê.'
  }
}

function eventLabel(id: string): string {
  return titles[id] ?? id
}

// ETA giây → "Mm Ss" cho dễ đọc.
function formatEta(sec: number): string {
  if (sec <= 0) return '—'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return m > 0 ? `${m}m ${s}s` : `${s}s`
}

onMounted(() => {
  load()
  loadTitles()
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
    const c = await waitingroom.updateConfig({ ...form })
    form.rate = c.rate
    form.tokenTtlSeconds = c.tokenTtlSeconds
    form.admitTtlSeconds = c.admitTtlSeconds
    saved.value = true
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Lưu thất bại.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div style="margin-bottom: 20px">
    <h1>Waiting Room</h1>
    <p class="muted">
      Van chặn spike khi flash sale. Cấu hình áp dụng ngay cho bộ thả (admission), không cần
      khởi động lại service.
    </p>
  </div>

  <!-- Thống kê hàng chờ theo thời gian thực -->
  <div class="panel" style="margin-bottom: 24px">
    <div class="row between" style="align-items: center; margin-bottom: 12px">
      <h2 style="margin: 0">Hàng chờ hiện tại</h2>
      <span class="muted" style="font-size: 13px">
        <template v-if="lastRefreshed">
          Cập nhật {{ lastRefreshed.toLocaleTimeString('vi-VN') }} · tự làm mới mỗi 5s
        </template>
      </span>
    </div>

    <div v-if="statsError" class="alert">{{ statsError }}</div>

    <p v-if="!statsError && stats.length === 0" class="muted">
      Hiện không có sự kiện nào đang có hàng chờ.
    </p>

    <table v-else-if="stats.length > 0" class="table">
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
        <tr v-for="s in stats" :key="s.eventId">
          <td>{{ eventLabel(s.eventId) }}</td>
          <td style="text-align: right">{{ s.waiting.toLocaleString('vi-VN') }}</td>
          <td style="text-align: right">{{ s.admittedTotal.toLocaleString('vi-VN') }}</td>
          <td style="text-align: right">{{ s.ratePerSecond }}/s</td>
          <td style="text-align: right">{{ formatEta(s.etaSeconds) }}</td>
          <td>
            <span v-if="s.soldOut" class="badge badge--CANCELLED">Hết vé</span>
            <span v-else class="badge badge--ON_SALE">Đang thả</span>
          </td>
        </tr>
      </tbody>
    </table>
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
