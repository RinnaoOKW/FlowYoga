<script setup>
defineProps({ name: { type: String, required: true } })
const paths = {
  members: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87',
  coach: 'M12 12v9 M3 7l9-4 9 4-9 4-9-4Z M5 9v6c4 3 10 3 14 0V9',
  search: 'm21 21-4.3-4.3',
  plus: 'M12 5v14 M5 12h14',
  arrow: 'M7 17 17 7 M7 7h10v10',
  refresh: 'M20 7v5h-5 M4 17v-5h5 M5.5 6a8 8 0 0 1 13.5 2 M18.5 18a8 8 0 0 1-13.5-2',
  close: 'm6 6 12 12 M18 6 6 18',
  check: 'm5 12 4 4L19 6',
  pause: 'M9 7v10 M15 7v10',
  chevron: 'm9 5 7 7-7 7',
  edit: 'm16 3 5 5-12 12-6 1 1-6L16 3Z M14 5l5 5',
}
</script>
<template><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle v-if="name === 'search'" cx="10.8" cy="10.8" r="7.5"/><circle v-if="name === 'members'" cx="9" cy="7" r="4"/><path :d="paths[name] || paths.arrow"/></svg></template>
