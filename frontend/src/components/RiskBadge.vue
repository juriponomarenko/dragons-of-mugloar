<script setup lang="ts">
import { computed } from 'vue'
import type { RiskLevel } from '@/api/types'

const props = defineProps<{ risk: RiskLevel; label?: string }>()

const classes: Record<RiskLevel, string> = {
  SAFE: 'badge-success',
  MODERATE: 'badge-warning',
  DANGEROUS: 'badge-danger',
}

const text = computed(() => props.label ?? props.risk.toLowerCase())
</script>

<template>
  <span class="badge" :class="classes[risk]" :title="`Risk: ${risk.toLowerCase()}`">{{
    text
  }}</span>
</template>
