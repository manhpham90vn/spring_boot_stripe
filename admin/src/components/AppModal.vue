<script setup lang="ts">
// Modal dùng chung: backdrop + panel. Đóng khi click nền hoặc nút Huỷ.
// Nội dung & nút truyền qua slot mặc định và slot #footer.
defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div class="modal__backdrop" @click.self="emit('close')">
    <div class="modal">
      <h2>{{ title }}</h2>
      <slot />
    </div>
  </div>
</template>
