<script setup lang="ts">
import { computed } from 'vue'
import type { AdView } from '@/api/types'
import RiskBadge from './RiskBadge.vue'
import { compact, gold, percent, turns } from '@/utils/format'

const props = defineProps<{ ad: AdView; disabled?: boolean }>()
const emit = defineEmits<{ solve: [adId: string] }>()

const barClass = computed(() => {
  if (props.ad.risk === 'SAFE') return 'is-safe'
  if (props.ad.risk === 'MODERATE') return 'is-moderate'
  return 'is-dangerous'
})

const expiresSoon = computed(() => props.ad.expiresIn <= 1)
</script>

<template>
  <article
    class="ad card"
    :class="{ 'is-recommended': ad.recommended, 'is-ineligible': !ad.eligible }"
    :data-ad-id="ad.adId"
  >
    <header class="ad__header">
      <div class="ad__badges">
        <span v-if="ad.recommended" class="badge badge-accent">★ Recommended</span>
        <RiskBadge :risk="ad.risk" :label="ad.probability" />
        <span
          v-if="ad.encryption !== 'none'"
          class="badge badge-muted"
          title="This ad arrived encoded"
        >
          decoded
        </span>
      </div>
      <div class="ad__reward" :title="`Reward: ${ad.reward} gold`">
        <span class="ad__reward-value">{{ gold(ad.reward) }}</span>
        <span class="ad__reward-unit">gold</span>
      </div>
    </header>

    <p class="ad__message">{{ ad.message }}</p>

    <div
      class="ad__meter"
      :class="barClass"
      :aria-label="`Success chance ${percent(ad.successRate)}`"
    >
      <div class="ad__meter-fill" :style="{ width: percent(ad.successRate) }"></div>
    </div>

    <dl class="ad__stats">
      <div>
        <dt title="Estimated chance of success for this label">Chance</dt>
        <dd>{{ percent(ad.successRate) }}</dd>
      </div>
      <div>
        <dt title="Chance × reward: the gold to expect on average">Expected</dt>
        <dd>{{ compact(ad.expectedValue) }}</dd>
      </div>
      <div>
        <dt title="Expected gold minus the expected cost of a lost life (and a reputation discount); the advisor ranks by this">
          Score
        </dt>
        <dd>{{ compact(ad.score) }}</dd>
      </div>
      <div :class="{ 'is-urgent': expiresSoon }">
        <dt>Expires</dt>
        <dd>{{ turns(ad.expiresIn) }}</dd>
      </div>
    </dl>

    <ul v-if="ad.notes.length" class="ad__notes muted">
      <li v-for="note in ad.notes" :key="note">{{ note }}</li>
    </ul>

    <footer class="ad__footer">
      <button
        class="btn btn-sm"
        :class="ad.recommended ? 'btn-primary' : ''"
        type="button"
        :disabled="disabled"
        @click="emit('solve', ad.adId)"
      >
        Solve
      </button>
    </footer>
  </article>
</template>

<style scoped>
.ad {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  position: relative;
}

.ad.is-recommended {
  border-color: var(--accent);
  box-shadow:
    0 0 0 1px var(--accent),
    var(--shadow);
}

.ad.is-ineligible {
  opacity: 0.72;
}

.ad__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.ad__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ad__reward {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--gold);
  white-space: nowrap;
}

.ad__reward-value {
  font-size: 1.3rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.ad__reward-unit {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.ad__message {
  font-size: 1rem;
  line-height: 1.4;
}

.ad__meter {
  height: 6px;
  border-radius: 999px;
  background: var(--surface-2);
  overflow: hidden;
}

.ad__meter-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.3s;
}

.is-safe .ad__meter-fill {
  background: var(--success);
}

.is-moderate .ad__meter-fill {
  background: var(--warning);
}

.is-dangerous .ad__meter-fill {
  background: var(--danger);
}

.ad__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.ad__stats dt {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--muted);
}

.ad__stats dd {
  margin: 0;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.ad__stats .is-urgent dd {
  color: var(--danger);
}

.ad__notes {
  margin: 0;
  padding-left: 18px;
  font-size: 0.85rem;
}

.ad__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
