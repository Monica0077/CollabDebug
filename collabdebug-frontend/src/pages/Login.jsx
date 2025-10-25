import React, { useState } from "react";
import API from "../api/api";
import { useNavigate } from "react-router-dom";

const Login = () => {
  const [formData, setFormData] = useState({ username: "", password: "" });
  const [showRegister, setShowRegister] = useState(false);
  const [regData, setRegData] = useState({ username: "", password: "" });
  const navigate = useNavigate();

  const handleChange = e => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleRegChange = e => {
    setRegData({ ...regData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async e => {
    e.preventDefault();
    try {
      const res = await API.post("/api/auth/login", formData);
      const token = res.data?.token;
      if (!token) {
        return alert("Login failed: no token returned");
      }
      localStorage.setItem("token", token);
      window.dispatchEvent(new Event("auth"));
      navigate("/dashboard");
    } catch (err) {
      console.error(err);
      alert("Login failed: " + (err.response?.data || err.message));
    }
  };

  const handleRegister = async e => {
    e.preventDefault();
    try {
      await API.post("/api/auth/register", regData);
      // auto-login after successful register
      const loginRes = await API.post("/api/auth/login", regData);
      const token = loginRes.data?.token;
      if (token) {
        localStorage.setItem("token", token);
        window.dispatchEvent(new Event("auth"));
        navigate("/dashboard");
      } else {
        alert("Registered, but auto-login failed. Please login manually.");
        setShowRegister(false);
      }
    } catch (err) {
      console.error(err);
      alert("Registration failed: " + (err.response?.data || err.message));
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-left">
        <div className="auth-left-inner">
          <h1>CollabDebug</h1>
          <p className="lead">Collaborative debugging sandboxes - run code, share sessions, fix faster.</p>
          <ul className="promo-list">
            <li>Isolated sandboxes (Python / Java / Node)</li>
            <li>JWT-secured sessions</li>
            <li>Shareable container logs</li>
          </ul>
        </div>
      </div>

      <div className="auth-right">
        <div className="auth-card">
          {!showRegister ? (
            <>
              <h2>Sign in to CollabDebug</h2>
              <form onSubmit={handleSubmit} className="auth-form">
                <label className="input-label">Username</label>
                <input
                  type="text"
                  name="username"
                  placeholder="Your username"
                  value={formData.username}
                  onChange={handleChange}
                  required
                  className="input-field"
                />

                <label className="input-label">Password</label>
                <input
                  type="password"
                  name="password"
                  placeholder="Your password"
                  value={formData.password}
                  onChange={handleChange}
                  required
                  className="input-field"
                />

                <button type="submit" className="btn btn-primary auth-submit">Login</button>
              </form>

              <div className="auth-footer">
                <span>New to CollabDebug?</span>
                <button className="btn btn-outline" onClick={() => setShowRegister(true)}>Create account</button>
              </div>
            </>
          ) : (
            <>
              <h2>Create account</h2>
              <form onSubmit={handleRegister} className="auth-form">
                <label className="input-label">Username</label>
                <input type="text" name="username" placeholder="Username" value={regData.username} onChange={handleRegChange} required className="input-field" />
                <label className="input-label">Password</label>
                <input type="password" name="password" placeholder="Password" value={regData.password} onChange={handleRegChange} required className="input-field" />
                <button type="submit" className="btn btn-primary auth-submit">Register & Sign in</button>
              </form>

              <div className="auth-footer">
                <span>Already have an account?</span>
                <button className="btn btn-outline" onClick={() => setShowRegister(false)}>Back to login</button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default Login;
