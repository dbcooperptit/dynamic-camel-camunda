import { useState, useCallback, useEffect } from 'react'
import './App.css'
import VisualWorkflowBuilder from './components/VisualWorkflowBuilder'
import WorkflowRunner from './components/WorkflowRunner'
import DeploymentList from './components/DeploymentList'
import Dashboard from './components/Dashboard'
import CamelRouteBuilder from './components/CamelRouteBuilder'

type TabType = 'dashboard' | 'builder' | 'camel' | 'runner' | 'deployments';

function App() {
  const [activeTab, setActiveTab] = useState<TabType>('dashboard')
  const [refreshTrigger, setRefreshTrigger] = useState(0)
  const [isDarkMode, setIsDarkMode] = useState(() => {
    const saved = localStorage.getItem('theme')
    return saved === 'dark'
  })

  // Apply theme to document
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', isDarkMode ? 'dark' : 'light')
    localStorage.setItem('theme', isDarkMode ? 'dark' : 'light')
  }, [isDarkMode])

  // Listen for navigation events from Dashboard
  useEffect(() => {
    const handleNavigate = (e: CustomEvent<string>) => {
      // Map old tab names to new ones
      const tabMap: Record<string, TabType> = {
        'visual-builder': 'builder',
        'builder': 'builder',
        'camel': 'camel',
      }
      const tab = tabMap[e.detail] || e.detail
      setActiveTab(tab as TabType)
    }
    window.addEventListener('navigate', handleNavigate as EventListener)
    return () => window.removeEventListener('navigate', handleNavigate as EventListener)
  }, [])

  const handleDeploySuccess = useCallback(() => {
    setRefreshTrigger(prev => prev + 1)
  }, [])

  const toggleTheme = () => setIsDarkMode(prev => !prev)

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-left">
          <h1>⚡ Camunda Workflow</h1>
        </div>
        <nav className="tabs">
          <button
            className={activeTab === 'dashboard' ? 'active' : ''}
            onClick={() => setActiveTab('dashboard')}
          >
            <span className="tab-icon">📊</span>
            <span className="tab-label">Dashboard</span>
          </button>
          <button
            className={activeTab === 'builder' ? 'active' : ''}
            onClick={() => setActiveTab('builder')}
          >
            <span className="tab-icon">🎨</span>
            <span className="tab-label">BPMN Builder</span>
          </button>
          <button
            className={activeTab === 'camel' ? 'active' : ''}
            onClick={() => setActiveTab('camel')}
          >
            <span className="tab-icon">🐪</span>
            <span className="tab-label">Camel Routes</span>
          </button>
          <button
            className={activeTab === 'runner' ? 'active' : ''}
            onClick={() => setActiveTab('runner')}
          >
            <span className="tab-icon">▶️</span>
            <span className="tab-label">Runner</span>
          </button>
          <button
            className={activeTab === 'deployments' ? 'active' : ''}
            onClick={() => setActiveTab('deployments')}
          >
            <span className="tab-icon">📦</span>
            <span className="tab-label">Deployments</span>
          </button>
        </nav>
        <div className="header-actions">
          <button className="theme-toggle" onClick={toggleTheme} title={isDarkMode ? 'Light Mode' : 'Dark Mode'}>
            {isDarkMode ? '☀️' : '🌙'}
          </button>
        </div>
      </header>

      <main className="main-content">
        {activeTab === 'dashboard' && <Dashboard />}
        {activeTab === 'builder' && <VisualWorkflowBuilder onDeploySuccess={handleDeploySuccess} />}
        {activeTab === 'camel' && <CamelRouteBuilder />}
        {activeTab === 'runner' && <WorkflowRunner />}
        {activeTab === 'deployments' && <DeploymentList refreshTrigger={refreshTrigger} />}
      </main>

      <footer className="app-footer">
        <p>Powered by Camunda BPM 7.21 + Apache Camel 4.9 + Spring Boot 3.3.6 + React Flow</p>
      </footer>
    </div>
  )
}

export default App

