<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useVenuesStore } from '../stores/venues'
import { useAsync } from '../composables/useAsync'
import { errorMessage } from '../lib/errors'
import PageHeader from '../components/PageHeader.vue'
import AppModal from '../components/AppModal.vue'
import type { VenuePayload, VenueResponse } from '../types/venues'

const store = useVenuesStore()
const { error, loading, run: load } = useAsync(() => store.fetchAll(), 'Không tải được danh sách.')

const modalOpen = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const form = reactive<VenuePayload>({ name: '', address: '', city: '' })

onMounted(load)

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', address: '', city: '' })
  modalOpen.value = true
}

function openEdit(v: VenueResponse) {
  editingId.value = v.id
  Object.assign(form, { name: v.name, address: v.address ?? '', city: v.city ?? '' })
  modalOpen.value = true
}

async function save() {
  saving.value = true
  error.value = null
  try {
    if (editingId.value) await store.update(editingId.value, { ...form })
    else await store.create({ ...form })
    modalOpen.value = false
  } catch (e) {
    error.value = errorMessage(e, 'Lưu thất bại.')
  } finally {
    saving.value = false
  }
}

async function remove(v: VenueResponse) {
  if (!confirm(`Xoá địa điểm "${v.name}"?`)) return
  try {
    await store.remove(v.id)
  } catch (e) {
    error.value = errorMessage(e, 'Xoá thất bại.')
  }
}
</script>

<template>
  <PageHeader title="Địa điểm" subtitle="Quản lý các venue dùng cho sự kiện.">
    <template #actions>
      <button class="btn btn--primary" @click="openCreate">+ Thêm địa điểm</button>
    </template>
  </PageHeader>

  <div v-if="error" class="alert">{{ error }}</div>
  <div v-if="loading" class="spinner">Đang tải…</div>

  <div v-else class="panel" style="padding: 0">
    <table class="table">
      <thead>
        <tr>
          <th>Tên</th>
          <th>Địa chỉ</th>
          <th>Thành phố</th>
          <th style="width: 140px"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="v in store.list" :key="v.id">
          <td>{{ v.name }}</td>
          <td class="muted">{{ v.address || '—' }}</td>
          <td class="muted">{{ v.city || '—' }}</td>
          <td>
            <div class="row">
              <button class="btn btn--sm" @click="openEdit(v)">Sửa</button>
              <button class="btn btn--sm btn--danger" @click="remove(v)">Xoá</button>
            </div>
          </td>
        </tr>
        <tr v-if="store.list.length === 0">
          <td colspan="4" class="muted" style="text-align: center; padding: 28px">
            Chưa có địa điểm nào.
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <AppModal v-if="modalOpen" :title="editingId ? 'Sửa địa điểm' : 'Thêm địa điểm'" @close="modalOpen = false">
    <form @submit.prevent="save">
      <div class="field">
        <label>Tên *</label>
        <input v-model="form.name" required maxlength="200" />
      </div>
      <div class="field">
        <label>Địa chỉ</label>
        <input v-model="form.address" maxlength="500" />
      </div>
      <div class="field">
        <label>Thành phố</label>
        <input v-model="form.city" maxlength="120" />
      </div>
      <div class="row between" style="margin-top: 18px">
        <button type="button" class="btn" @click="modalOpen = false">Huỷ</button>
        <button class="btn btn--primary" :disabled="saving">
          {{ saving ? 'Đang lưu…' : 'Lưu' }}
        </button>
      </div>
    </form>
  </AppModal>
</template>
