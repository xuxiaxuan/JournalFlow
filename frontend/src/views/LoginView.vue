<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Bot, CheckCircle2, ShieldCheck } from 'lucide-vue-next'
import { login } from '../session'

const router = useRouter()
const route = useRoute()
const username = ref('author1')
const password = ref('123456')
const loading = ref(false)
const error = ref('')

const accounts = [
  ['author1', '作者'], ['reviewer1', '审稿专家'], ['editor1', '编辑'], ['admin', '管理员']
]

function choose(name) {
  username.value = name
  password.value = '123456'
}

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await login(username.value, password.value)
    router.push(route.query.redirect || '/dashboard')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login-layout">
    <div class="login-story">
      <span class="eyebrow"><Bot :size="15" /> AI ASSISTED PUBLISHING</span>
      <h1>让每一次学术判断<br><em>有依据，可追溯。</em></h1>
      <p>覆盖投稿、形式审查、同行评议、修改复审与正式出版。AI 负责提效，最终决定始终由人作出。</p>
      <div class="feature-strip">
        <span><CheckCircle2 /> 全流程版本链</span>
        <span><ShieldCheck /> 人工确认机制</span>
      </div>
    </div>

    <form class="login-card" @submit.prevent="submit">
      <div><span class="overline">欢迎回来</span><h2>登录工作台</h2><p class="muted">选择演示身份，或输入你的账号。</p></div>
      <div class="account-grid">
        <button v-for="item in accounts" :key="item[0]" type="button" :class="{ active: username === item[0] }" @click="choose(item[0])">
          <strong>{{ item[1] }}</strong><small>{{ item[0] }}</small>
        </button>
      </div>
      <label>用户名<input v-model="username" autocomplete="username" placeholder="请输入用户名" /></label>
      <label>密码<input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" /></label>
      <p v-if="error" class="error-message">{{ error }}</p>
      <button class="button button-primary submit-button" :disabled="loading">
        {{ loading ? '正在登录…' : '进入 JournalFlow' }} <ArrowRight :size="18" />
      </button>
      <p class="demo-tip">演示账号默认密码：<code>123456</code></p>
    </form>
  </section>
</template>
