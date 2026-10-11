import './AppShell.css';

export default function AppShell({ topNav, sidebar, content, island, offline }) {
  return (
    <div className="app-shell">
      {offline}
      {topNav}
      <div className="app-body">
        {sidebar}
        <div className="app-content">
          {content}
        </div>
      </div>
      {island}
    </div>
  );
}
