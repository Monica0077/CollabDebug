import React from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";

export default function NavBar() {
  const navigate = useNavigate();
  const location = useLocation();
  const token = localStorage.getItem("token");

  const handleLogout = () => {
    localStorage.removeItem("token");
    // dispatch auth event so App updates
    window.dispatchEvent(new Event("auth"));
    navigate("/login");
  };

  return (
    <header className={"site-header" + (token ? " site-header-auth" : "")}>
      <div className="brand">
        <Link to="/">CollabDebug</Link>
      </div>

      <nav className="nav-center">
        <Link to="/" className={location.pathname === '/' ? 'active' : ''}>
          Home
        </Link>
        {token ? (
          <>
            <Link 
              to="/features" 
              className={location.pathname === '/features' ? 'active' : ''}
            >
              Features
            </Link>
            <Link 
              to="/docs" 
              className={location.pathname === '/docs' ? 'active' : ''}
            >
              Docs
            </Link>
          </>
        ) : null}
      </nav>

      <div className="nav-actions">
        {!token ? (
          <>
            <Link className="btn btn-outline" to="/login">Login</Link>
            <Link className="btn btn-primary" to="/register">Register</Link>
          </>
        ) : (
          <>
            <Link 
              to="/dashboard" 
              className={`btn ${location.pathname === '/dashboard' ? 'btn-primary' : 'btn-outline'}`}
            >
              Dashboard
            </Link>
            <button className="btn btn-danger" onClick={handleLogout}>Logout</button>
          </>
        )}
      </div>
    </header>
  );
}

