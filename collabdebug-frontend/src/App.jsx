import React, { useState, useEffect } from "react";
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from "react-router-dom";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./components/Dashboard";
import Home from "./pages/Home";
import Features from "./pages/Features";
import Docs from "./pages/Docs";
import NavBar from "./components/NavBar";
import "./App.css";
import SessionRoom from "./components/SessionRoom";

function App() {
  const [token, setToken] = useState(localStorage.getItem("token"));

  useEffect(() => {
    const handler = () => setToken(localStorage.getItem("token"));
    // storage event fires for other windows; custom 'auth' event used for same-tab updates
    window.addEventListener("storage", handler);
    window.addEventListener("auth", handler);

    // Add window beforeunload handler for auto-logout
    const handleBeforeUnload = (e) => {
      // If it's a refresh (navigation within same origin), don't logout
      if (e.currentTarget.performance.navigation.type === 1) {
        return;
      }
      localStorage.removeItem("token");
      setToken(null);
    };
    window.addEventListener("beforeunload", handleBeforeUnload);

    return () => {
      window.removeEventListener("storage", handler);
      window.removeEventListener("auth", handler);
      window.removeEventListener("beforeunload", handleBeforeUnload);
    };
  }, []);

  // wrapper to hide NavBar on auth pages
  function AuthAwareWrapper() {
    const location = useLocation();
    const pathname = location.pathname;
    const isAuthPage = pathname === '/login' || pathname === '/register';
    return (
      <>
        {!isAuthPage && <NavBar />}
      </>
    );
  }

  return (
    <Router>
      <AuthAwareWrapper />
      <div className="app-content">
        <Routes>
          
          <Route path="/login" element={<Login />} />
          {/* always allow register page */}
          <Route path="/register" element={<Register />} />

          <Route path="/home" element={<Home />} />
          <Route path="/dashboard" element={token ? <Dashboard/> : <Navigate to="/login" />} />
          <Route path="/session/:sessionId" element={token ? <SessionRoom/> : <Navigate to="/login" />} />
          <Route path="/features" element={token ? <Features /> : <Navigate to="/login" />} />
          <Route path="/docs" element={token ? <Docs /> : <Navigate to="/login" />} />

          {/* Home: redirect to login if not authenticated */}
          <Route path="/" element={token ? <Home /> : <Navigate to="/login" />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
