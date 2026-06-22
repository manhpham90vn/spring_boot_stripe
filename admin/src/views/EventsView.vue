<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useEventsStore } from '../stores/events'
import { useVenuesStore } from '../stores/venues'
import { useAsync } from '../composables/useAsync'
import { errorMessage } from '../lib/errors'
import { formatDateTime, localInputToIso } from '../lib/format'
import PageHeader from '../components/PageHeader.vue'
import AppModal from '../components/AppModal.vue'

const router = useRouter()
const eventsStore = useEventsStore()
const venuesStore = useVenuesStore()

// Trang Sự kiện cần cả danh sách event lẫn venue (cho dropdown khi tạo).
const { error, loading, run: load } = useAsync(
  () => Promise.all([eventsStore.fetchList(), venuesStore.ensureLoaded()]),
  'Không tải được dữ liệu.',
)

const modalOpen = ref(false)
const saving = ref(false)
const form = reactive({
  venueId: '',
  title: '',
  description: '',
  startsAt: '',
  salesStartAt: '',
  salesEndAt: '',
})

onMounted(load)

function openCreate() {
  if (venuesStore.list.length === 0) {
    error.value = 'Hãy tạo ít nhất một địa điểm trước khi tạo sự kiện.'
    return
  }
  Object.assign(form, {
    venueId: venuesStore.list[0].id,
    title: '',
    description: '',
    startsAt: '',
    salesStartAt: '',
    salesEndAt: '',
  })
  modalOpen.value = true
}

async function create() {
  saving.value = true
  error.value = null
  try {
    const ev = await eventsStore.create({
      venueId: form.venueId,
      title: form.title,
      description: form.description,
      startsAt: localInputToIso(form.startsAt)!,
      salesStartAt: localInputToIso(form.salesStartAt),
      salesEndAt: localInputToIso(form.salesEndAt),
    })
    modalOpen.value = false
    router.push({ name: 'event-detail', params: { id: ev.id } })
  } catch (e) {
    error.value = errorMessage(e, 'Tạo sự kiện thất bại.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PageHeader title="Sự kiện" subtitle="Tất cả sự kiện (gồm cả bản nháp DRAFT).">
    <template #actions>
      <button class="btn btn--primary" @click="openCreate">+ Tạo sự kiện</button>
    </template>
  </PageHeader>

  <div v-if="error" class="alert">{{ error }}</div>
  <div v-if="loading" class="spinner">Đang tải…</div>

  <div v-else class="panel" style="padding: 0">
    <table class="table">
      <thead>
        <tr>
          <th>Tiêu đề</th>
          <th>Trạng thái</th>
          <th>Bắt đầu</th>
          <th>Địa điểm</th>
          <th style="width: 90px"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="e in eventsStore.list" :key="e.id">
          <td>{{ e.title }}</td>
          <td><span :class="`badge badge--${e.status}`">{{ e.status }}</span></td>
          <td class="muted">{{ formatDateTime(e.startsAt) }}</td>
          <td class="muted">{{ e.venue?.name || '—' }}</td>
          <td>
            <RouterLink class="btn btn--sm" :to="{ name: 'event-detail', params: { id: e.id } }">
              Quản lý
            </RouterLink>
          </td>
        </tr>
        <tr v-if="eventsStore.list.length === 0">
          <td colspan="5" class="muted" style="text-align: center; padding: 28px">
            Chưa có sự kiện nào.
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <AppModal v-if="modalOpen" title="Tạo sự kiện" @close="modalOpen = false">
    <form @submit.prevent="create">
      <div class="field">
        <label>Tiêu đề *</label>
        <input v-model="form.title" required maxlength="300" />
      </div>
      <div class="field">
        <label>Địa điểm *</label>
        <select v-model="form.venueId" required>
          <option v-for="v in venuesStore.list" :key="v.id" :value="v.id">{{ v.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>Mô tả</label>
        <textarea v-model="form.description" rows="3" />
      </div>
      <div class="field">
        <label>Thời gian diễn ra *</label>
        <input v-model="form.startsAt" type="datetime-local" required />
      </div>
      <div class="grid-2">
        <div class="field">
          <label>Mở bán</label>
          <input v-model="form.salesStartAt" type="datetime-local" />
        </div>
        <div class="field">
          <label>Đóng bán</label>
          <input v-model="form.salesEndAt" type="datetime-local" />
        </div>
      </div>
      <div class="row between" style="margin-top: 18px">
        <button type="button" class="btn" @click="modalOpen = false">Huỷ</button>
        <button class="btn btn--primary" :disabled="saving">
          {{ saving ? 'Đang tạo…' : 'Tạo & cấu hình vé' }}
        </button>
      </div>
    </form>
  </AppModal>
</template>
