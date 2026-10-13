<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../auth.js'
import { roles, rolePath } from '../roles.js'
import StudioIllustration from '../components/StudioIllustration.vue'

const props = defineProps({ roleKey: { type: String, required: true } })
const router = useRouter()
const role = computed(() => roles[props.roleKey])
const loginName = ref('')
const password = ref('')
const showPassword = ref(false)
const busy = ref(false)
const error = ref('')
const isDevelopment = import.meta.env.DEV
watch(() => props.roleKey, () => { loginName.value = ''; password.value = ''; error.value = ''; showPassword.value = false })

async function submit() {
  if (busy.value) return
  error.value = ''
  if (!loginName.value.trim() || !password.value) { error.value = '请填写登录账号和密码。'; return }
  busy.value = true
  try {
    const result = await login({ loginName: loginName.value.trim(), password: password.value, role: role.value.code })
    await router.replace(`/${rolePath(result.user.role)}`)
  } catch (failure) { error.value = failure.message }
  finally { busy.value = false }
}

function fillDemo() { loginName.value = role.value.account; password.value = 'FlowYoga@123'; error.value = '' }
</script>

<template>
  <div class="login-page" :class="`role-${roleKey}`">
    <header class="site-header">
      <RouterLink class="brand" to="/login/member" aria-label="FlowYoga 会员登录首页"><img src="/favicon.svg" alt=""/><span>FlowYoga<span class="brand-dot">.</span></span></RouterLink>
      <span class="header-caption">瑜伽馆管理系统<span class="caption-line"></span>在流动中，找到平衡</span>
    </header>

    <main class="login-layout">
      <section class="hero-panel" aria-label="FlowYoga 的练习理念">
        <span class="hero-eyebrow"><span class="tiny-sun">✳</span>{{ role.eyebrow }}</span>
        <h1>{{ role.headline }}</h1>
        <p class="hero-description">{{ role.description }}</p>
        <StudioIllustration />
        <div class="hero-bottom"><span class="breathing-dot"></span><span>一呼一吸，都是新的开始。</span><span class="hero-number">01 / FLOW</span></div>
      </section>

      <section class="login-side" aria-label="账号登录">
        <div class="login-card">
          <nav class="role-nav" aria-label="选择登录身份" :class="{ 'is-busy': busy }">
            <RouterLink v-for="(item, key) in roles" :key="key" :to="`/login/${key}`" :class="{ selected: key === roleKey }" @click="busy && $event.preventDefault()">{{ item.label }}</RouterLink>
          </nav>
          <div class="form-heading"><span class="section-eyebrow">WELCOME TO FLOWYOGA</span><h2>{{ role.label }}登录</h2><p>很高兴与你相遇，让我们从这里开始。</p></div>
          <form novalidate @submit.prevent="submit" :aria-busy="busy">
            <div class="form-field"><label for="login-name">登录账号</label><div class="input-wrap"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 20v-2a7 7 0 0 1 14 0v2"/></svg><input id="login-name" v-model="loginName" name="username" autocomplete="username" :placeholder="`请输入${role.label}账号`" maxlength="50" :disabled="busy" required/></div></div>
            <div class="form-field"><label for="login-password">登录密码</label><div class="input-wrap"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/><path d="M12 14v3"/></svg><input id="login-password" v-model="password" name="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" maxlength="72" :disabled="busy" required/><button class="visibility-button" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/><path v-if="!showPassword" d="m3 3 18 18"/></svg></button></div></div>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <button class="primary-button" type="submit" :disabled="busy"><span>{{ busy ? '正在登录…' : role.button }}</span><span v-if="!busy" aria-hidden="true">↗</span><span v-else class="spinner" aria-hidden="true"></span></button>
          </form>
          <details class="login-help"><summary>登录遇到问题？</summary><p>请确认账号对应的身份。如果忘记密码或账号已停用，请联系馆内管理员处理。</p></details>
          <div class="form-divider"></div>
          <p class="login-note"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 8 4v6c0 5-8 9-8 9s-8-4-8-9V7l8-4Z"/><path d="m8 12 3 3 5-6"/></svg>安心登录，专注每一次练习。</p>
          <details v-if="isDevelopment" class="demo-accounts"><summary>本地演示账号</summary><div><p>账号 <code>{{ role.account }}</code> · 密码 <code>FlowYoga@123</code></p><button type="button" @click="fillDemo" :disabled="busy">填入演示账号 <span aria-hidden="true">↗</span></button></div></details>
        </div>
        <p class="side-footer">MOVE WITH INTENTION. LIVE WITH BALANCE.</p>
      </section>
    </main>
    <footer class="site-footer"><span>© {{ new Date().getFullYear() }} FlowYoga</span><span>让日常管理，也有舒展的空间。</span></footer>
  </div>
</template>
