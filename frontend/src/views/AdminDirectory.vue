<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { request, mutation, clearSession } from '../auth.js'
import AdminIcon from '../components/AdminIcon.vue'
const props = defineProps({ kind: { type: String, required: true } })
const router = useRouter()
const isCoach = computed(() => props.kind === 'coaches')
const label = computed(() => isCoach.value ? '教练' : '会员')
const api = computed(() => isCoach.value ? '/api/course/admin/coaches' : '/api/member/admin/members')
const keyword = ref(''), appliedKeyword = ref(''), status = ref(''), page = ref(1), size = 10
const data = ref({ items: [], total: 0, statistics: { total: 0, active: 0, disabled: 0 }, pendingChanges: [] })
const loading = ref(true), loadError = ref(''), toast = ref(null), retrying = ref('')
const formOpen = ref(false), editing = ref(null), saving = ref(false), formError = ref(''), showPassword = ref(false)
const confirm = ref(null), modal = ref(null)
const form = reactive({ displayName: '', phone: '', loginName: '', initialPassword: '', specialty: '', introduction: '', status: 'ACTIVE' })
let operationId = crypto.randomUUID(), loadSequence = 0, previousFocus, toastTimer, previousBodyOverflow = ''
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / size)))
const range = computed(() => data.value.total ? `${(page.value - 1) * size + 1}–${Math.min(page.value * size, data.value.total)}` : '0')
const specialtyTags = text => (text || '').split(/[、,，]/).map(item => item.trim()).filter(Boolean).slice(0, 3)
function formatDate(value) { if (!value) return '资料待同步'; return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(`${value}Z`)) }
function notify(message, tone = 'success') { clearTimeout(toastTimer); toast.value = { message, tone }; toastTimer = setTimeout(() => { toast.value = null }, 6500) }
async function handleFailure(failure) { if (failure.status === 401) { clearSession(); await router.replace('/login/admin') } }
async function load(quiet = false) {
  const sequence = ++loadSequence
  if (!quiet) loading.value = true
  loadError.value = ''
  try {
    const result = await request(`${api.value}?${new URLSearchParams({ keyword: appliedKeyword.value, status: status.value, page: String(page.value), size: String(size) })}`)
    if (sequence !== loadSequence) return
    data.value = result
    if (page.value > totalPages.value) { page.value = totalPages.value; return load() }
  } catch (failure) { if (sequence === loadSequence) { loadError.value = failure.message; await handleFailure(failure) } }
  finally { if (sequence === loadSequence) loading.value = false }
}
function search() { appliedKeyword.value = keyword.value.trim(); page.value = 1; load() }
function filterStatus() { page.value = 1; load() }
function changePage(value) { page.value = value; load() }
function close() {
  if (saving.value) return
  formOpen.value = false; confirm.value = null
  nextTick(() => previousFocus?.focus())
}
async function openForm(person = null) {
  previousFocus = document.activeElement; editing.value = person
  Object.assign(form, { displayName: person?.displayName || '', phone: person?.phone || '', loginName: person?.loginName || '', initialPassword: '', specialty: person?.specialty || '', introduction: person?.introduction || '', status: person?.status || 'ACTIVE' })
  operationId = crypto.randomUUID(); formError.value = ''; showPassword.value = false; formOpen.value = true
  await nextTick(); modal.value?.querySelector('input')?.focus()
}
watch(form, () => { if (!saving.value) operationId = crypto.randomUUID() }, { deep: true })
watch(() => formOpen.value || !!confirm.value, open => {
  if (open) { previousBodyOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden' }
  else document.body.style.overflow = previousBodyOverflow
})
function validate() {
  if (!form.displayName.trim()) return '请填写姓名。'
  if (!/^1[3-9]\d{9}$/.test(form.phone)) return '请填写有效的 11 位手机号。'
  if (!editing.value && !/^[A-Za-z0-9][A-Za-z0-9_.-]{2,49}$/.test(form.loginName)) return '账号需为 3–50 位字母、数字、下划线、点或短横线。'
  if (!editing.value && !form.initialPassword.trim()) return '请填写初始密码。'
  if (!editing.value && (form.initialPassword.length < 8 || new TextEncoder().encode(form.initialPassword).length > 72)) return '初始密码至少 8 位，按 UTF-8 编码后最多 72 字节。'
  return ''
}
async function save() {
  if (saving.value) return
  formError.value = validate(); if (formError.value) return
  saving.value = true
  const payload = { requestId: operationId, displayName: form.displayName.trim(), phone: form.phone, specialty: isCoach.value ? form.specialty.trim() : null, introduction: isCoach.value ? form.introduction.trim() : null }
  if (editing.value) payload.version = editing.value.version
  else Object.assign(payload, { loginName: form.loginName, initialPassword: form.initialPassword, status: form.status })
  try {
    const result = await mutation(editing.value ? `${api.value}/${editing.value.id}` : api.value, payload, editing.value ? 'PUT' : 'POST')
    saving.value = false; close(); notify(result.state === 'DONE' ? `${label.value}资料已保存` : result.message, result.state === 'DONE' ? 'success' : 'pending'); await load()
  } catch (failure) { formError.value = failure.message; if (failure.status !== 0) operationId = crypto.randomUUID(); await handleFailure(failure) }
  finally { saving.value = false }
}
async function askStatus(person) {
  previousFocus = document.activeElement; confirm.value = person; formError.value = ''; operationId = crypto.randomUUID()
  await nextTick(); modal.value?.querySelector('button')?.focus()
}
async function toggleStatus() {
  if (saving.value) return
  saving.value = true; formError.value = ''
  const person = confirm.value, nextStatus = person.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    const result = await mutation(`${api.value}/${person.id}/status`, { requestId: operationId, version: person.version, status: nextStatus }, 'PATCH')
    saving.value = false; close(); notify(result.state === 'DONE' ? `${person.displayName}已${nextStatus === 'ACTIVE' ? '启用' : '停用'}` : result.message, result.state === 'DONE' ? 'success' : 'pending'); await load()
  } catch (failure) { formError.value = failure.message; if (failure.status !== 0) operationId = crypto.randomUUID(); await handleFailure(failure) }
  finally { saving.value = false }
}
async function retry(task) {
  if (retrying.value) return
  retrying.value = task.requestId
  try { const result = await mutation(`${api.value}/changes/${task.requestId}/retry`); notify(result.message, result.state === 'DONE' ? 'success' : 'pending') }
  catch (failure) { notify(failure.message, 'error'); await handleFailure(failure) }
  finally { retrying.value = ''; await load(true) }
}
function trap(event) {
  if (event.key === 'Escape') { event.preventDefault(); close(); return }
  if (event.key !== 'Tab') return
  const items = Array.from(modal.value.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]'))
  const first = items[0], last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
watch(() => props.kind, () => { ++loadSequence; keyword.value = ''; appliedKeyword.value = ''; status.value = ''; page.value = 1; close(); toast.value = null; data.value = { items: [], total: 0, statistics: { total: 0, active: 0, disabled: 0 }, pendingChanges: [] }; load() }, { immediate: true })
const poll = setInterval(() => { if (data.value.pendingChanges.length && !formOpen.value && !confirm.value && !loading.value && !retrying.value) load(true) }, 10000)
onUnmounted(() => { ++loadSequence; clearInterval(poll); clearTimeout(toastTimer); document.body.style.overflow = previousBodyOverflow })
</script>
<template>
  <main class="directory-page">
    <div class="directory-heading"><div><span class="admin-eyebrow">{{ isCoach ? 'THE PEOPLE WHO GUIDE' : 'EVERY MEMBER MATTERS' }}</span><h1>{{ label }}管理<span>。</span></h1><p>{{ isCoach ? '记录每一位教练的专长，让陪伴更有方向。' : '从第一次相遇开始，好好记录每一份信任。' }}</p></div><button class="admin-primary" type="button" @click="openForm()"><AdminIcon name="plus"/>新增{{ label }}</button></div>
    <div v-if="toast" class="admin-toast" :class="`tone-${toast.tone}`" role="status"><AdminIcon :name="toast.tone === 'success' ? 'check' : 'refresh'"/><span>{{ toast.message }}</span><button class="admin-icon-button" type="button" aria-label="关闭提示" @click="toast = null"><AdminIcon name="close"/></button></div>
    <section class="directory-stats" :aria-label="`${label}概况`"><div class="stat-card"><span>{{ label }}总数</span><strong>{{ loading && !data.statistics.total ? '—' : data.statistics.total }}<small>位</small></strong><AdminIcon :name="isCoach ? 'coach' : 'members'"/></div><div class="stat-card"><span>正常{{ isCoach ? '授课' : '使用' }}</span><strong>{{ loading && !data.statistics.total ? '—' : data.statistics.active }}<small>位</small></strong><span class="stat-note"><i></i>账号已启用</span></div><div class="stat-card"><span>已停用</span><strong>{{ loading && !data.statistics.total ? '—' : data.statistics.disabled }}<small>位</small></strong><span class="stat-note">保留档案与历史记录</span></div></section>
    <details v-if="data.pendingChanges.length" class="pending-panel" open><summary><AdminIcon name="refresh"/> {{ data.pendingChanges.length }} 条资料同步处理中</summary><p>系统会自动重试。同步完成前，请勿重复新增或修改同一份档案。</p><div v-for="task in data.pendingChanges" :key="task.requestId"><span><strong>{{ task.loginName }}</strong> · {{ task.operation === 'CREATE' ? '账号建档' : task.operation === 'STATUS' ? '状态变更' : '资料修改' }}<small>{{ task.lastError || '正在同步账号与档案' }}</small></span><button type="button" class="admin-text-button" :disabled="!!retrying" @click="retry(task)">{{ retrying === task.requestId ? '同步中…' : '重试同步' }}</button></div></details>
    <section class="directory-list" :aria-label="`${label}档案列表`">
      <div class="list-heading"><h2>{{ label }}档案 <span>{{ data.total }}</span></h2><span class="list-caption">每一份资料，都来自真实记录</span></div>
      <div class="directory-toolbar"><form class="directory-search" role="search" @submit.prevent="search"><AdminIcon name="search"/><input v-model="keyword" :aria-label="`搜索${label}`" placeholder="搜索姓名、手机号或账号" maxlength="100"/><button type="submit" :disabled="loading">搜索</button></form><div class="toolbar-actions"><select v-model="status" :aria-label="`${label}状态筛选`" @change="filterStatus"><option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">已停用</option></select><button class="admin-icon-button" type="button" :disabled="loading" aria-label="刷新列表" @click="load()"><AdminIcon name="refresh"/></button></div></div>
      <div v-if="loadError" class="directory-empty"><AdminIcon name="refresh"/><h3>暂时无法读取档案</h3><p role="alert">{{ loadError }}</p><button type="button" class="admin-secondary" @click="load()">重新加载</button></div>
      <div v-else-if="loading" class="directory-empty" aria-live="polite"><span class="admin-spinner"></span><p>正在读取{{ label }}档案…</p></div>
      <div v-else-if="!data.items.length" class="directory-empty"><AdminIcon :name="isCoach ? 'coach' : 'members'"/><h3>{{ appliedKeyword || status ? '没有符合条件的档案' : `还没有${label}档案` }}</h3><p>{{ appliedKeyword || status ? '试试其他姓名、手机号或状态。' : `新增第一份${label}资料，让管理从这里开始。` }}</p><button v-if="!appliedKeyword && !status" class="admin-secondary" type="button" @click="openForm()">新增{{ label }}</button></div>
      <div v-else class="directory-table-wrap"><table><thead><tr><th>{{ label }}编号</th><th>{{ label }}信息</th><th>联系手机号</th><th v-if="isCoach">教学专长</th><th>状态</th><th>建档日期</th><th class="actions-heading">操作</th></tr></thead><tbody><tr v-for="person in data.items" :key="person.userId"><td class="person-number">{{ person.id ? `${isCoach ? 'C' : 'M'}${person.id.padStart(4, '0')}` : '待同步' }}</td><td><div class="person-cell"><span class="person-avatar" :class="{ 'coach-avatar': isCoach }">{{ person.displayName.slice(0, 1) }}</span><div><strong>{{ person.displayName }}</strong><span>{{ person.loginName }}</span></div></div></td><td class="phone-cell">{{ person.phone }}</td><td v-if="isCoach"><div class="specialty-tags"><span v-for="tag in specialtyTags(person.specialty)" :key="tag">{{ tag }}</span><small v-if="!specialtyTags(person.specialty).length">尚未填写</small></div></td><td><span class="person-status" :class="person.syncState !== 'DONE' ? 'pending' : person.status.toLowerCase()"><i></i>{{ person.syncState !== 'DONE' ? '同步中' : person.status === 'ACTIVE' ? '正常' : '已停用' }}</span></td><td class="date-cell">{{ formatDate(person.createdAt) }}</td><td><div class="row-actions"><button type="button" class="admin-text-button" :disabled="person.syncState !== 'DONE'" @click="openForm(person)">编辑</button><span></span><button type="button" class="admin-text-button" :class="{ 'muted-action': person.status === 'ACTIVE' }" :disabled="person.syncState !== 'DONE'" @click="askStatus(person)">{{ person.status === 'ACTIVE' ? '停用' : '启用' }}</button></div></td></tr></tbody></table></div>
      <div v-if="!loading && !loadError" class="directory-pagination"><span>显示 {{ range }} 条，共 {{ data.total }} 位{{ label }}</span><div><button type="button" :disabled="page <= 1" aria-label="上一页" @click="changePage(page - 1)"><AdminIcon name="chevron"/></button><span>{{ page }} / {{ totalPages }}</span><button type="button" :disabled="page >= totalPages" aria-label="下一页" @click="changePage(page + 1)"><AdminIcon name="chevron"/></button></div></div>
    </section>
    <p class="directory-footnote"><span></span>{{ isCoach ? '停用教练会关闭登录权限，已有排课需要另行调整。' : '停用会员会关闭登录权限，已有预约与业务历史仍然保留。' }}</p>
  </main>
  <Teleport to="body">
    <div v-if="formOpen || confirm" class="admin-modal-backdrop" @keydown="trap" @click.self="close">
      <section v-if="formOpen" ref="modal" class="directory-drawer" role="dialog" aria-modal="true" aria-labelledby="person-form-title"><header><div><span class="admin-eyebrow">{{ editing ? 'KEEP IT UP TO DATE' : 'A NEW CONNECTION' }}</span><h2 id="person-form-title">{{ editing ? '编辑' : '新增' }}{{ label }}</h2><p>{{ editing ? '更新资料，保持每一次联系畅通。' : '建立档案与登录账号，开启新的连接。' }}</p></div><button class="admin-icon-button" type="button" :disabled="saving" aria-label="关闭表单" @click="close"><AdminIcon name="close"/></button></header>
        <form class="person-form" @submit.prevent="save" :aria-busy="saving">
          <div class="drawer-fields"><div class="drawer-section-label">基本资料 <span>01</span></div><label>姓名 <span>*</span><input v-model="form.displayName" name="displayName" autocomplete="off" placeholder="请输入姓名" maxlength="50" :disabled="saving" required/></label><label>手机号 <span>*</span><input v-model="form.phone" name="phone" type="tel" inputmode="tel" autocomplete="off" placeholder="请输入 11 位手机号" maxlength="11" :disabled="saving" required/><small>手机号在所有会员、教练与管理员账号中唯一。</small></label>
          <template v-if="isCoach"><label>教学专长<input v-model="form.specialty" placeholder="例如：哈他瑜伽、普拉提" maxlength="255" :disabled="saving"/></label><label>教练简介<textarea v-model="form.introduction" rows="3" placeholder="记录教学经验与练习理念" maxlength="1000" :disabled="saving"></textarea><small>{{ form.introduction.length }} / 1000</small></label></template>
          <div class="drawer-section-label">登录账号 <span>02</span></div><label>登录账号 <span v-if="!editing">*</span><input v-model="form.loginName" name="loginName" autocomplete="off" placeholder="3–50 位字母或数字等" maxlength="50" :disabled="saving || !!editing" :required="!editing"/><small>{{ editing ? '登录账号保持不变。' : `账号创建后仅用于${label}身份登录。` }}</small></label><template v-if="!editing"><label>初始密码 <span>*</span><div class="drawer-password"><input v-model="form.initialPassword" name="initialPassword" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 8 位" maxlength="72" :disabled="saving" required/><button type="button" :disabled="saving" @click="showPassword = !showPassword" :aria-label="showPassword ? '隐藏初始密码' : '显示初始密码'">{{ showPassword ? '隐藏' : '显示' }}</button></div><small>请通过馆内约定的方式告知本人，密码不会出现在列表中。</small></label><label>初始状态<select v-model="form.status" :disabled="saving"><option value="ACTIVE">正常 · 可登录</option><option value="DISABLED">停用 · 暂不可登录</option></select></label></template></div>
          <footer><p v-if="formError" class="admin-error" role="alert">{{ formError }}</p><div><button class="admin-secondary" type="button" :disabled="saving" @click="close">取消</button><button class="admin-primary" type="submit" :disabled="saving"><span v-if="saving" class="admin-spinner"></span>{{ saving ? '正在保存…' : editing ? '保存修改' : `创建${label}` }}</button></div></footer>
        </form>
      </section>
      <section v-else ref="modal" class="status-dialog" role="alertdialog" aria-modal="true" aria-labelledby="status-title" aria-describedby="status-description"><span class="status-dialog-icon"><AdminIcon :name="confirm.status === 'ACTIVE' ? 'pause' : 'check'"/></span><h2 id="status-title">{{ confirm.status === 'ACTIVE' ? '停用' : '启用' }}{{ confirm.displayName }}？</h2><p id="status-description">{{ confirm.status === 'ACTIVE' ? `停用后，该${label}将无法登录${isCoach ? '，也不能安排新课' : '，也不能进行新预约'}。已有业务与历史记录会保留。` : `启用后，该${label}可使用原账号与密码重新登录。` }}</p><p v-if="formError" class="admin-error" role="alert">{{ formError }}</p><div><button class="admin-secondary" type="button" :disabled="saving" @click="close">取消</button><button class="admin-primary" type="button" :disabled="saving" @click="toggleStatus">{{ saving ? '正在更新…' : confirm.status === 'ACTIVE' ? '确认停用' : '确认启用' }}</button></div></section>
    </div>
  </Teleport>
</template>
