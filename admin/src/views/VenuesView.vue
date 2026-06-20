<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { venues } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { VenuePayload, VenueResponse } from '../api/types'

const list = ref<VenueResponse[] | null>(null)
const error = ref<string | null>(null)

const modalOpen = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const form = reactive<VenuePayload>({ name: '', address: '', city: '' })

async function load() {
  error.value = null
  try {
    list.value = await venues.list()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Không tải được danh sách.'
  }
}
onMounted(load)

function openCreate() {
  editingId.value = null
  form.name = ''
  form.address = ''
  form.city = ''
  modalOpen.value = true
}

function openEdit(v: VenueResponse) {
  editingId.value = v.id
  form.name = v.name
  form.address = v.address ?? ''
  form.city = v.city ?? ''
  modalOpen.value = true
}

async function save() {
  saving.value = true
  error.value = null
  try {
    if (editingId.value) await venues.update(editingId.value, { ...form })
    else await venues.create({ ...form })
    modalOpen.value = false
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Lưu thất bại.'
  } finally {
    saving.value = false
  }
}

async function remove(v: VenueResponse) {
  if (!confirm(`Xoá địa điểm "${v.name}"?`)) return
  try {
    await venues.remove(v.id)
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Xoá thất bại.'
  }
}
</script>

<template>
  <div class="row between" style="margin-bottom: 20px">
    <div>
      <h1>Địa điểm</h1>
      <p class="muted">Quản lý các venue dùng cho sự kiện.</p>
    </div>
    <button class="btn btn--primary" @click="openCreate">+ Thêm địa điểm</button>
  </div>

  <div v-if="error" class="alert">{{ error }}</div>
  <div v-if="!list" class="spinner">Đang tải…</div>

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
        <tr v-for="v in list" :key="v.id">
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
        <tr v-if="list.length === 0">
          <td colspan="4" class="muted" style="text-align: center; padding: 28px">
            Chưa có địa điểm nào.
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- Modal create/edit -->
  <div v-if="modalOpen" class="modal__backdrop" @click.self="modalOpen = false">
    <div class="modal">
      <h2>{{ editingId ? 'Sửa địa điểm' : 'Thêm địa điểm' }}</h2>
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
    </div>
  </div>
</template>
