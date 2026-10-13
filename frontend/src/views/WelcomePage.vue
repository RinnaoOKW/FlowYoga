<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { session, request, logout, clearSession } from '../auth.js'
import { roles, permissionNames } from '../roles.js'
import StudioIllustration from '../components/StudioIllustration.vue'

const props = defineProps({ roleKey: { type: String, required: true } })
const role = computed(() => roles[props.roleKey])
const router = useRouter()
const error = ref('')
const busy = ref(false)
const loading = ref(true)
let expiryTimer
const expiryLabel = computed(() => session.expiresAt ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(session.expiresAt)) : '—')

async function leave() {
  busy.value = true; error.value = ''
  try { await logout(); await router.replace(`/login/${props.roleKey}`) }
  catch (failure) { error.value = failure.message }
  finally { busy.value = false }
}
onMounted(async () => {
  try {
    const current = await request(`/api/user/workspaces/${props.roleKey}`)
    session.user = current.user; session.expiresAt = current.expiresAt
    expiryTimer = setTimeout(() => { clearSession(); router.replace(`/login/${props.roleKey}`) }, Math.max(0, new Date(current.expiresAt).getTime() - Date.now()))
  } catch (failure) {
    if (failure.status === 401) { clearSession(); await router.replace(`/login/${props.roleKey}`) }
    else error.value = failure.message
  } finally { loading.value = false }
})
onUnmounted(() => clearTimeout(expiryTimer))
</script>

<template>
  <div class="workspace-page" :class="`role-${roleKey}`">
    <header class="workspace-header"><RouterLink class="brand" :to="`/${roleKey}`"><img src="/favicon.svg" alt=""/><span>FlowYoga<span class="brand-dot">.</span></span></RouterLink><div class="workspace-user"><span class="role-badge">{{ role.label }}空间</span><span>{{ session.user?.displayName }}</span><button class="text-button" type="button" :disabled="busy" @click="leave">{{ busy ? '退出中…' : '退出登录' }} <span aria-hidden="true">↗</span></button></div></header>
    <main class="workspace-main">
      <nav class="workspace-nav" aria-label="空间导航"><a href="#welcome" class="active">我的首页</a><a href="#account">账号信息</a></nav>
      <section id="welcome" class="welcome-hero"><div><span class="section-eyebrow">YOUR SPACE. YOUR FLOW.</span><h1>欢迎回来，<br/>{{ session.user?.displayName }}<span class="welcome-dot">。</span></h1><p>{{ role.welcome }}</p><a class="inline-link" href="#account">查看我的账号 <span aria-hidden="true">↗</span></a></div><StudioIllustration /></section>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <section id="account" class="account-section"><div class="account-heading"><div><span class="section-eyebrow">MY ACCOUNT</span><h2>你的账号信息</h2></div><span class="status-label"><span></span>{{ loading ? '正在确认身份' : error ? '身份确认暂时不可用' : '登录状态正常' }}</span></div><div class="account-grid"><div><span class="detail-label">登录账号</span><strong>{{ session.user?.loginName }}</strong></div><div><span class="detail-label">账号身份</span><strong>{{ session.user?.roleLabel }}</strong></div><div><span class="detail-label">联系手机号</span><strong>{{ session.user?.phone }}</strong></div><div><span class="detail-label">本次登录有效期至</span><strong>{{ expiryLabel }}</strong></div></div><div class="permission-row"><span class="detail-label">身份对应的馆内服务</span><div><span v-for="permission in session.user?.permissions || []" :key="permission">{{ permissionNames[permission] || permission }}</span></div><p>本页用于确认登录身份。馆内业务功能正在逐步开放。</p></div></section>
    </main>
    <footer class="site-footer"><span>© {{ new Date().getFullYear() }} FlowYoga</span><span>在流动中，找到平衡。</span></footer>
  </div>
</template>
