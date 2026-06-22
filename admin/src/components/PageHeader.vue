<script setup lang="ts">
// Tiêu đề trang: h1 + mô tả phụ, kèm slot #actions cho nút bên phải.
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <div class="row between" style="margin-bottom: 20px">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle" class="muted">{{ subtitle }}</p>
    </div>
    <slot name="actions" />
  </div>
</template>
