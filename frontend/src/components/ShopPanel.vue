<script setup lang="ts">
import type { ShopItemView } from '@/api/types'
import { gold } from '@/utils/format'

defineProps<{ items: ShopItemView[]; playerGold: number; disabled?: boolean }>()
const emit = defineEmits<{ buy: [itemId: string] }>()
</script>

<template>
  <section class="shop card">
    <h2 class="card-title">Shop</h2>
    <p class="muted shop__hint">
      Upgrades raise your dragon's level; the healing potion adds a life. Each purchase costs a
      turn, even a failed one.
    </p>
    <ul class="shop__list">
      <li
        v-for="item in items"
        :key="item.id"
        class="shop__item"
        :class="{ 'is-suggested': item.suggested, 'is-unaffordable': !item.affordable }"
      >
        <div class="shop__info">
          <div class="shop__name">
            {{ item.name }}
            <span v-if="item.suggested" class="badge badge-accent">suggested</span>
            <span v-if="item.timesBought" class="badge badge-muted">×{{ item.timesBought }}</span>
          </div>
          <div class="shop__cost" :class="{ 'is-short': !item.affordable }">
            {{ gold(item.cost) }} gold<span v-if="!item.affordable">
              · {{ gold(item.cost - playerGold) }} more needed</span
            >
          </div>
        </div>
        <button
          class="btn btn-sm"
          :class="item.suggested ? 'btn-primary' : ''"
          type="button"
          :disabled="disabled || !item.affordable"
          :title="item.affordable ? `Buy ${item.name}` : `Need ${item.cost - playerGold} more gold`"
          @click="emit('buy', item.id)"
        >
          Buy
        </button>
      </li>
    </ul>
    <p v-if="!items.length" class="muted">The shop is empty.</p>
  </section>
</template>

<style scoped>
.shop__hint {
  font-size: 0.85rem;
  margin-bottom: 12px;
}

.shop__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.shop__item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}

.shop__item.is-suggested {
  border-color: var(--accent);
}

.shop__item.is-unaffordable {
  opacity: 0.7;
}

.shop__info {
  min-width: 0;
}

.shop__name {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 0.95rem;
}

.shop__cost {
  font-size: 0.85rem;
  color: var(--gold);
  font-variant-numeric: tabular-nums;
}

.shop__cost.is-short {
  color: var(--muted);
}
</style>
