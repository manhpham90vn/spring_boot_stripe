<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { events, ticketTypes, venues } from '../api/endpoints'
import { ApiError } from '../api/client'
import { formatMoney, isoToLocalInput, localInputToIso } from '../lib/format'
import type {
  EventDetailResponse,
  EventStatus,
  TicketTypePayload,
  TicketTypeResponse,
  VenueResponse,
} from '../api/types'

const props = defineProps<{ id: string }>()
const router = useRouter()

const event = ref<EventDetailResponse | null>(null)
const venueList = ref<VenueResponse[]>([])
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

const STATUSES: EventStatus[] = ['DRAFT', 'ON_SALE', 'CLOSED', 'CANCELLED']

// --- form sửa sự kiện ---
const ev = reactive({
  venueId: '',
  title: '',
  description: '',
  startsAt: '',
  salesStartAt: '',
  salesEndAt: '',
})
const savingEvent = ref(false)

// --- modal hạng vé ---
const ttModalOpen = ref(false)
const ttEditingId = ref<string | null>(null)
const savingTt = ref(false)
const tt = reactive<TicketTypePayload>({
  name: '',
  description: '',
  priceMinor: 0,
  currency: 'VND',
  maxPerOrder: 4,
})

async function load() {
  error.value = null
  try {
    const [detail, vns] = await Promise.all([events.detail(props.id), venues.list()])
    event.value = detail
    venueList.value = vns
    ev.venueId = detail.venue?.id ?? ''
    ev.title = detail.title
    ev.description = detail.description ?? ''
    ev.startsAt = isoToLocalInput(detail.startsAt)
    ev.salesStartAt = isoToLocalInput(detail.salesStartAt)
    ev.salesEndAt = isoToLocalInput(detail.salesEndAt)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Không tải được sự kiện.'
  }
}
onMounted(load)

function flash(msg: string) {
  notice.value = msg
  setTimeout(() => (notice.value = null), 2500)
}

async function saveEvent() {
  savingEvent.value = true
  error.value = null
  try {
    event.value = await events.update(props.id, {
      venueId: ev.venueId,
      title: ev.title,
      description: ev.description,
      startsAt: localInputToIso(ev.startsAt)!,
      salesStartAt: localInputToIso(ev.salesStartAt),
      salesEndAt: localInputToIso(ev.salesEndAt),
    })
    flash('Đã lưu thông tin sự kiện.')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Lưu thất bại.'
  } finally {
    savingEvent.value = false
  }
}

async function changeStatus(status: EventStatus) {
  error.value = null
  try {
    event.value = await events.changeStatus(props.id, status)
    flash(`Trạng thái → ${status}`)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Đổi trạng thái thất bại.'
  }
}

async function removeEvent() {
  if (!confirm('Xoá sự kiện này?')) return
  try {
    await events.remove(props.id)
    router.push({ name: 'events' })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Xoá thất bại.'
  }
}

function openTtCreate() {
  ttEditingId.value = null
  tt.name = ''
  tt.description = ''
  tt.priceMinor = 0
  tt.currency = 'VND'
  tt.maxPerOrder = 4
  ttModalOpen.value = true
}

function openTtEdit(t: TicketTypeResponse) {
  ttEditingId.value = t.id
  tt.name = t.name
  tt.description = t.description ?? ''
  tt.priceMinor = t.priceMinor
  tt.currency = t.currency
  tt.maxPerOrder = t.maxPerOrder
  ttModalOpen.value = true
}

async function saveTt() {
  savingTt.value = true
  error.value = null
  try {
    const payload: TicketTypePayload = {
      name: tt.name,
      description: tt.description,
      priceMinor: Number(tt.priceMinor),
      currency: tt.currency.toUpperCase(),
      maxPerOrder: Number(tt.maxPerOrder),
    }
    if (ttEditingId.value) await ticketTypes.update(props.id, ttEditingId.value, payload)
    else await ticketTypes.add(props.id, payload)
    ttModalOpen.value = false
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Lưu hạng vé thất bại.'
  } finally {
    savingTt.value = false
  }
}

async function removeTt(t: TicketTypeResponse) {
  if (!confirm(`Xoá hạng vé "${t.name}"?`)) return
  try {
    await ticketTypes.remove(props.id, t.id)
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Xoá thất bại.'
  }
}
</script>

<template>
  <RouterLink :to="{ name: 'events' }" class="muted">← Tất cả sự kiện</RouterLink>

  <div v-if="error" class="alert" style="margin-top: 14px">{{ error }}</div>
  <div v-if="notice" class="alert alert--ok" style="margin-top: 14px">{{ notice }}</div>
  <div v-if="!event" class="spinner">Đang tải…</div>

  <template v-else>
    <div class="row between" style="margin: 12px 0 20px">
      <h1>{{ event.title }}</h1>
      <span :class="`badge badge--${event.status}`">{{ event.status }}</span>
    </div>

    <!-- Trạng thái -->
    <div class="panel" style="margin-bottom: 20px">
      <h2>Trạng thái phát hành</h2>
      <p class="muted" style="margin-bottom: 12px">
        DRAFT → ON_SALE để mở bán. Đặt CLOSED khi ngừng bán, CANCELLED khi huỷ.
      </p>
      <div class="row">
        <button
          v-for="s in STATUSES"
          :key="s"
          class="btn btn--sm"
          :class="{ 'btn--primary': s === event.status }"
          :disabled="s === event.status"
          @click="changeStatus(s)"
        >
          {{ s }}
        </button>
      </div>
    </div>

    <!-- Thông tin -->
    <div class="panel" style="margin-bottom: 20px">
      <h2>Thông tin sự kiện</h2>
      <form @submit.prevent="saveEvent">
        <div class="field">
          <label>Tiêu đề *</label>
          <input v-model="ev.title" required maxlength="300" />
        </div>
        <div class="field">
          <label>Địa điểm *</label>
          <select v-model="ev.venueId" required>
            <option v-for="v in venueList" :key="v.id" :value="v.id">{{ v.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>Mô tả</label>
          <textarea v-model="ev.description" rows="3" />
        </div>
        <div class="grid-2">
          <div class="field">
            <label>Thời gian diễn ra *</label>
            <input v-model="ev.startsAt" type="datetime-local" required />
          </div>
          <div class="field">
            <label>Mở bán</label>
            <input v-model="ev.salesStartAt" type="datetime-local" />
          </div>
        </div>
        <div class="field" style="max-width: calc(50% - 8px)">
          <label>Đóng bán</label>
          <input v-model="ev.salesEndAt" type="datetime-local" />
        </div>
        <button class="btn btn--primary" :disabled="savingEvent">
          {{ savingEvent ? 'Đang lưu…' : 'Lưu thông tin' }}
        </button>
      </form>
    </div>

    <!-- Hạng vé -->
    <div class="panel" style="padding: 0; margin-bottom: 20px">
      <div class="row between" style="padding: 18px 20px 0">
        <h2 style="margin: 0">Hạng vé</h2>
        <button class="btn btn--primary btn--sm" @click="openTtCreate">+ Thêm hạng vé</button>
      </div>
      <table class="table" style="margin-top: 12px">
        <thead>
          <tr>
            <th>Tên</th>
            <th>Giá</th>
            <th>Tối đa/đơn</th>
            <th style="width: 140px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in event.ticketTypes" :key="t.id">
            <td>
              {{ t.name }}
              <div v-if="t.description" class="muted" style="font-size: 13px">
                {{ t.description }}
              </div>
            </td>
            <td>{{ formatMoney(t.priceMinor, t.currency) }}</td>
            <td class="muted">{{ t.maxPerOrder }}</td>
            <td>
              <div class="row">
                <button class="btn btn--sm" @click="openTtEdit(t)">Sửa</button>
                <button class="btn btn--sm btn--danger" @click="removeTt(t)">Xoá</button>
              </div>
            </td>
          </tr>
          <tr v-if="event.ticketTypes.length === 0">
            <td colspan="4" class="muted" style="text-align: center; padding: 24px">
              Chưa có hạng vé nào.
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <button class="btn btn--danger" @click="removeEvent">Xoá sự kiện</button>
  </template>

  <!-- Modal hạng vé -->
  <div v-if="ttModalOpen" class="modal__backdrop" @click.self="ttModalOpen = false">
    <div class="modal">
      <h2>{{ ttEditingId ? 'Sửa hạng vé' : 'Thêm hạng vé' }}</h2>
      <form @submit.prevent="saveTt">
        <div class="field">
          <label>Tên *</label>
          <input v-model="tt.name" required maxlength="120" />
        </div>
        <div class="field">
          <label>Mô tả</label>
          <input v-model="tt.description" maxlength="500" />
        </div>
        <div class="grid-2">
          <div class="field">
            <label>Giá (đơn vị nhỏ nhất) *</label>
            <input v-model.number="tt.priceMinor" type="number" min="0" required />
          </div>
          <div class="field">
            <label>Tiền tệ *</label>
            <input v-model="tt.currency" maxlength="3" required style="text-transform: uppercase" />
          </div>
        </div>
        <div class="field">
          <label>Tối đa mỗi đơn *</label>
          <input v-model.number="tt.maxPerOrder" type="number" min="1" required />
        </div>
        <p class="muted" style="font-size: 13px">
          Ví dụ VND 250.000 → nhập <code>250000</code>. USD $50.00 → nhập <code>5000</code>.
        </p>
        <div class="row between" style="margin-top: 18px">
          <button type="button" class="btn" @click="ttModalOpen = false">Huỷ</button>
          <button class="btn btn--primary" :disabled="savingTt">
            {{ savingTt ? 'Đang lưu…' : 'Lưu' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
