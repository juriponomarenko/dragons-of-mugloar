<script setup lang="ts">
/**
 * One line the player should read: an error (red, announced assertively) or a notice such as
 * why a server-side run stopped early (accent, announced politely). Dismissable.
 */
withDefaults(
  defineProps<{ message: string | null; details?: string[]; tone?: 'error' | 'info' }>(),
  {
    details: () => [],
    tone: 'error',
  },
)
const emit = defineEmits<{ dismiss: [] }>()
</script>

<template>
  <Transition name="banner">
    <div
      v-if="message"
      class="banner"
      :class="`is-${tone}`"
      :role="tone === 'error' ? 'alert' : 'status'"
    >
      <div class="banner__body">
        <strong>{{ message }}</strong>
        <ul v-if="details.length" class="banner__details">
          <li v-for="d in details" :key="d">{{ d }}</li>
        </ul>
      </div>
      <button class="banner__close" type="button" aria-label="Dismiss" @click="emit('dismiss')">
        ×
      </button>
    </div>
  </Transition>
</template>

<style scoped>
.banner {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin: 12px 0;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid;
}

.banner.is-error {
  background: var(--danger-soft);
  color: var(--danger);
  border-color: var(--danger);
}

.banner.is-info {
  background: var(--accent-soft);
  color: var(--text);
  border-color: var(--accent);
}

.banner__details {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: 0.9rem;
}

.banner__close {
  border: 0;
  background: transparent;
  font-size: 1.4rem;
  line-height: 1;
  color: inherit;
  padding: 0 4px;
}

.banner-enter-active,
.banner-leave-active {
  transition: opacity 0.2s;
}

.banner-enter-from,
.banner-leave-to {
  opacity: 0;
}
</style>
