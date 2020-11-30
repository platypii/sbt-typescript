import { platformBrowserDynamic } from '@angular/platform-browser-dynamic'
import TodoApp from './app'
import { TodoStore } from './services/store'

platformBrowserDynamic().bootstrapModule(TodoApp)
