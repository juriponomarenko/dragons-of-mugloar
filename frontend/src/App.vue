<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header class="app-header">
    <div class="container app-header__inner">
      <RouterLink to="/" class="brand">
        <span class="brand__mark" aria-hidden="true">🐉</span>
        <span class="brand__name">Dragons of Mugloar</span>
      </RouterLink>
      <a
        class="app-header__link"
        href="https://dragonsofmugloar.com/doc/"
        target="_blank"
        rel="noopener noreferrer"
      >
        Game API docs
      </a>
    </div>
  </header>

  <main class="app-main container">
    <RouterView />
  </main>

  <footer class="app-footer container muted">
    Spring Boot + Vue 3 solution to the Bigbank test task.
  </footer>
</template>

<style scoped>
.app-header {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.app-header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 56px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--text);
  font-weight: 800;
  font-size: 1.1rem;
}

.brand__mark {
  font-size: 1.4rem;
}

.app-header__link {
  font-size: 0.9rem;
}

.app-main {
  flex: 1;
  padding-top: 20px;
  padding-bottom: 32px;
}

.app-footer {
  padding: 16px;
  font-size: 0.85rem;
  text-align: center;
}
</style>
