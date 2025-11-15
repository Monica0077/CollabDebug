import React, { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { getUsername } from "../utils/jwtDecoder";

export default function NavBar() {
  const navigate = useNavigate();
  const location = useLocation();
  const token = localStorage.getItem("token");
  const username = token ? getUsername(token) : null;
  const [showDropdown, setShowDropdown] = useState(false);

  const handleLogout = () => {
    localStorage.removeItem("token");
    // dispatch auth event so App updates
    window.dispatchEvent(new Event("auth"));
    navigate("/login");
  };

  const toggleDropdown = () => {
    setShowDropdown(!showDropdown);
  };

  const handleDropdownItemClick = () => {
    setShowDropdown(false);
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
            
            {/* User Profile Dropdown */}
            <div className="user-profile-dropdown">
              <button 
                className="user-profile-button"
                onClick={toggleDropdown}
                title={`Logged in as ${username}`}
              >
                <span className="user-avatar">👤</span>
                <span className="user-name">{username}</span>
                <span className={`dropdown-chevron ${showDropdown ? 'open' : ''}`}>▼</span>
              </button>

              {showDropdown && (
                <div className="dropdown-menu">
                  <div className="dropdown-header">
                    <div className="user-info">
                      <div className="user-avatar-large">👤</div>
                      <div className="user-details">
                        <div className="user-label">Logged in as</div>
                        <div className="user-username">{username}</div>
                      </div>
                    </div>
                  </div>
                  
                  <div className="dropdown-divider"></div>
                  
                  <button 
                    className="dropdown-item"
                    onClick={() => {
                      handleDropdownItemClick();
                      navigate("/dashboard");
                    }}
                  >
                    <span className="item-icon">📊</span>
                    <span className="item-label">Dashboard</span>
                  </button>
                  
                  <div className="dropdown-divider"></div>
                  
                  <button 
                    className="dropdown-item logout-item"
                    onClick={() => {
                      handleDropdownItemClick();
                      handleLogout();
                    }}
                  >
                    <span className="item-icon">🚪</span>
                    <span className="item-label">Logout</span>
                  </button>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </header>
  );
}

