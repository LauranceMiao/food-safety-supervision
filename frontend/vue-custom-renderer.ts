// Compile Vue components for the client while rendering into a custom host in Node.
export default {
  name: 'vue-custom-renderer',
  viteEnvironment: 'client',
  setup() {
    return { teardown() {} }
  }
}
