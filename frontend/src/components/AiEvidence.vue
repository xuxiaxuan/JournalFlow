<script setup>
import { ref } from 'vue'
import api from '../api'
const props = defineProps({ taskId: { type: Number, required: true } })
const result = ref(null), loading = ref(false), error = ref('')
const sources = { BODY: '正文直接匹配', VERSION_METADATA: '版本表单快照', UNVERIFIED: '待人工核实' }
async function load() {
  loading.value = true; error.value = ''
  try { result.value = await api.get(`/llm/tasks/${props.taskId}/evidence`) }
  catch (e) { error.value = e.message } finally { loading.value = false }
}
</script>
<template>
  <section class="evidence-panel">
    <h3>AI 结果原文溯源</h3>
    <button class="button button-small button-secondary" :disabled="loading" @click="load">{{ loading ? '正在核对…' : '查看字段依据' }}</button>
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <template v-if="result">
      <p class="muted">任务 #{{ result.taskId }} · v{{ result.versionNo }} · {{ result.scope }}</p>
      <small>来源文件 SHA-256：{{ result.fileHash }}</small>
      <p v-if="!result.textAvailable">正文不可提取；仅能核对表单快照。</p>
      <details v-for="(field, index) in result.fields" :key="index" :class="field.source">
        <summary>{{ field.field }}：{{ typeof field.value === 'string' ? field.value.slice(0, 100) : JSON.stringify(field.value) }} · {{ sources[field.source] }}</summary>
        <p>{{ field.value }}</p><small v-if="field.line">提取文本第 {{ field.line }} 个非空段</small><blockquote>{{ field.quote }}</blockquote>
      </details>
    </template>
  </section>
</template>
<style scoped>
.evidence-panel{border-top:1px solid #d5dfd4;padding-top:18px} details{padding:10px;border:1px solid #d9e1d8;border-radius:8px;margin-top:8px}
summary{cursor:pointer;overflow-wrap:anywhere}.UNVERIFIED{background:#fff4df}.BODY{background:#e9f3e9}blockquote{white-space:pre-wrap;overflow-wrap:anywhere;margin:10px 0;padding-left:12px;border-left:3px solid #668776}small{overflow-wrap:anywhere}
</style>
