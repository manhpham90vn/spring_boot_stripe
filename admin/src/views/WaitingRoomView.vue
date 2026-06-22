<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { waitingroom } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { AdmissionConfig } from '../api/types'

// Cấu hình van Waiting Room. Lưu ở Redis của waitingroom (store chính — kiến trúc không dùng
// PostgreSQL cho service này); cập nhật áp dụng NGAY cho bộ thả admission, không cần redeploy.
const form = reactive<AdmissionConfig>({ rate: 20, tokenTtlSeconds: 1800, admitTtlSeconds: 120 })
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const saved = ref(false)

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
onMounted(load)

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
