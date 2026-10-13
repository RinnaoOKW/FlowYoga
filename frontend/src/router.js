import { createRouter, createWebHistory } from 'vue-router'
import { ensureSession, session } from './auth.js'
import { roles, rolePath } from './roles.js'
import LoginPage from './views/LoginPage.vue'
import WelcomePage from './views/WelcomePage.vue'
import AdminLayout from './views/AdminLayout.vue'
import AdminDirectory from './views/AdminDirectory.vue'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 20 }
    return { top: 0 }
  },
  routes: [
    { path: '/', redirect: '/login/member' },
    ...Object.entries(roles).map(([key, role]) => ({ path: `/login/${key}`, component: LoginPage, props: { roleKey: key }, meta: { title: `${role.label}登录` } })),
    { path: '/admin', component: AdminLayout, meta: { role: 'ADMIN' }, children: [
      { path: '', redirect: '/admin/members' },
      { path: 'members', component: AdminDirectory, props: { kind: 'members' }, meta: { title: '会员管理' } },
      { path: 'coaches', component: AdminDirectory, props: { kind: 'coaches' }, meta: { title: '教练管理' } },
    ] },
    ...Object.entries(roles).filter(([key]) => key !== 'admin').map(([key, role]) => ({ path: `/${key}`, component: WelcomePage, props: { roleKey: key }, meta: { role: role.code, title: `${role.label}空间` } })),
    { path: '/:pathMatch(.*)*', redirect: '/login/member' },
  ],
})

router.beforeEach(async to => {
  document.title = `FlowYoga · ${to.meta.title || '登录'}`
  if (!to.meta.role) return true
  try { await ensureSession() } catch { return `/login/${to.meta.role.toLowerCase()}` }
  if (!session.user) return `/login/${to.meta.role.toLowerCase()}`
  if (session.user.role !== to.meta.role) return `/${rolePath(session.user.role)}`
  return true
})

export default router
