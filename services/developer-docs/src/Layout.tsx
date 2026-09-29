import { NavLink, Outlet } from 'react-router-dom';

export default function Layout() {
  return (
    <div className="page">
      <header className="site-header">
        <span className="brand">
          itunda <span className="brand-accent">developers</span>
        </span>
        <nav className="site-nav">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'header-link header-link-active' : 'header-link')}>
            Pay
          </NavLink>
          <NavLink to="/identity" className={({ isActive }) => (isActive ? 'header-link header-link-active' : 'header-link')}>
            Verify identity
          </NavLink>
          <NavLink to="/certificate" className={({ isActive }) => (isActive ? 'header-link header-link-active' : 'header-link')}>
            Verify signature
          </NavLink>
        </nav>
      </header>
      <main>
        <div className="container">
          <Outlet />
        </div>
      </main>
      <footer className="site-footer">
        <p>itunda Developers — building on itunda's own payments and identity infrastructure.</p>
      </footer>
    </div>
  );
}
