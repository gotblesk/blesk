import { Component } from 'react';

export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, retryKey: 0 };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('ErrorBoundary caught:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      const compact = this.props.compact;
      return (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          height: compact ? '100%' : '100vh',
          minHeight: compact ? 200 : undefined,
          background: compact ? 'transparent' : 'var(--bg, #08060f)',
          color: 'var(--text, rgba(255,255,255,0.7))',
          fontFamily: '-apple-system, sans-serif', gap: '12px',
        }}>
          <div style={{ fontSize: '16px', fontWeight: 700 }}>Что-то пошло не так</div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted, rgba(255,255,255,0.3))' }}>
            {this.state.error?.message || 'Неизвестная ошибка'}
          </div>
          <button
            onClick={() => { this.setState(prev => ({ hasError: false, error: null, retryKey: (prev.retryKey || 0) + 1 })); }}
            style={{
              padding: '8px 20px', borderRadius: '10px', border: 'none',
              background: 'var(--accent, #c8ff00)', color: 'var(--accent-text, #000)', fontSize: '13px', fontWeight: 600,
              cursor: 'pointer', marginTop: '8px',
            }}
          >
            Попробовать снова
          </button>
          <button
            onClick={() => window.location.reload()}
            style={{
              padding: '6px 16px', borderRadius: '8px', border: '1px solid var(--glass-border, rgba(255,255,255,0.1))',
              background: 'transparent', color: 'var(--text-muted, rgba(255,255,255,0.4))', fontSize: '11px',
              cursor: 'pointer',
            }}
          >
            Перезагрузить
          </button>
        </div>
      );
    }
    return <div key={this.state.retryKey} style={{ display: 'contents' }}>{this.props.children}</div>;
  }
}
