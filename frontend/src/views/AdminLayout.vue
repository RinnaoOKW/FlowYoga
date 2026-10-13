<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { session, request, logout, clearSession } from '../auth.js'
import AdminIcon from '../components/AdminIcon.vue'
const router = useRouter()
const busy = ref(false), error = ref('')
let expiryTimer
async function leave() {
  busy.value = true; error.value = ''
  try { await logout(); await router.replace('/login/admin') }
  catch (failure) { error.value = failure.message }
  finally { busy.value = false }
}
onMounted(async () => {
  try {
    const current = await request('/api/user/workspaces/admin')
    session.user = current.user; session.expiresAt = current.expiresAt
    expiryTimer = setTimeout(() => { clearSession(); router.replace('/login/admin') }, Math.max(0, new Date(current.expiresAt).getTime() - Date.now()))
  } catch (failure) {
    if (failure.status === 401 || failure.status === 403) { clearSession(); await router.replace('/login/admin') }
    else error.value = failure.message
  }
})
onUnmounted(() => clearTimeout(expiryTimer))
</script>
<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="brand" to="/admin/members"><img src="/favicon.svg" alt=""/><span>FlowYoga<span class="brand-dot">.</span></span></RouterLink>
      <div class="sidebar-label">STUDIO MANAGEMENT <span>馆内管理</span></div>
      <nav class="admin-nav" aria-label="管理员导航">
        <RouterLink to="/admin/members" active-class="is-active"><AdminIcon name="members"/><span>会员管理</span><AdminIcon class="nav-arrow" name="chevron"/></RouterLink>
        <RouterLink to="/admin/coaches" active-class="is-active"><AdminIcon name="coach"/><span>教练管理</span><AdminIcon class="nav-arrow" name="chevron"/></RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-quote"><span class="tiny-sun">✳</span><p>把日常打理好，<br/>让每一次练习从容发生。</p><small>MAKE ROOM FOR YOUR FLOW.</small></div>
        <div class="operator-card"><span class="operator-avatar">{{ session.user?.displayName?.slice(0, 1) || '管' }}</span><div><strong>{{ session.user?.displayName || '管理员' }}</strong><span>馆内管理员</span></div><button type="button" class="admin-icon-button" :disabled="busy" @click="leave" aria-label="退出登录"><AdminIcon name="arrow"/></button></div>
      </div>
    </aside>
    <div class="admin-main">
      <header class="admin-topbar"><div><span>管理工作台</span><AdminIcon name="chevron"/><strong>{{ $route.meta.title }}</strong></div><span class="admin-topbar-note"><span></span> FlowYoga · 单门店</span></header>
      <p v-if="error" class="admin-error" role="alert">{{ error }}</p>
      <RouterView />
      <footer class="admin-footer"><span>© {{ new Date().getFullYear() }} FlowYoga</span><span>每一份用心，都值得被好好记录。</span></footer>
    </div>
  </div>
</template>
