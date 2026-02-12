<template>
  <div class="admin-panel">
    <div class="glass-card">
      <AdminPanelNew />
    </div>
  </div>
</template>

<script setup lang="ts">
import AdminPanelNew from '../components/AdminPanelNew.vue'
</script>

<style scoped>
.admin-panel {
  position: relative;
  min-height: 100vh;
  padding: 1rem;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

@media (min-width: 768px) {
  .admin-panel {
    padding: 2rem;
  }
}

.glass-card {
  position: relative;
  z-index: 10;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 1rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  width: 100%;
  max-width: 1200px;
}

@media (min-width: 768px) {
  .glass-card {
    padding: 2rem;
  }
}
</style>
