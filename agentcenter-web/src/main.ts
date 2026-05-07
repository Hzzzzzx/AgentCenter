import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import './styles/themes.css'
import './styles/app.css'
import { useThemeStore } from './stores/theme'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

useThemeStore(pinia).initFromStorage()

app.mount('#app')
