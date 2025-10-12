import React, { useState } from "react";
import PageLayout from "../components/PageLayout";
import API from "../api/api";

const Register = () => {
  const [formData, setFormData] = useState({ username: "", password: "" });

  const handleChange = e => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async e => {
    e.preventDefault();
    try {
      const res = await API.post("/api/auth/register", formData);
      // backend returns a plain success message on register
      alert(res.data || "Registered successfully!");
      // redirect to login so user can authenticate
      window.location.href = "/login";
    } catch (err) {
      console.error(err);
      alert("Registration failed: " + (err.response?.data || err.message));
    }
  };

  return (
    <div className="container">
      <h1>Register</h1>
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          name="username"
          placeholder="Username"
          value={formData.username}
          onChange={handleChange}
          required
        />
        <input
          type="password"
          name="password"
          placeholder="Password"
          value={formData.password}
          onChange={handleChange}
          required
        />
        <button type="submit">Register</button>
      </form>
    </div>
  );
};

export default Register;
