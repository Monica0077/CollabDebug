import React from "react";
import { Link, useNavigate } from "react-router-dom";

export default function NavBar() {
  const navigate = useNavigate();
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
        <Link to="/">Home</Link>
        {token ? (
          <>
            <Link to="/features">Features</Link>
            <Link to="/docs">Docs</Link>
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
            <button className="btn btn-outline" onClick={() => navigate("/dashboard")}>Dashboard</button>
            <button className="btn btn-danger" onClick={handleLogout}>Logout</button>
          </>
        )}
      </div>
    </header>
  );
}

